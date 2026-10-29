/**
 * Verifica del comportamento del gestionale su schermo stretto.
 *
 * Stessa impostazione di sito/scripts/mobile-smoke.mjs — Chrome headless
 * guidato dal protocollo DevTools, nessuna dipendenza aggiunta — applicata al
 * guscio di AssociaGo. Il bersaglio è `/mobile-harness.html`, che monta i
 * componenti veri senza backend né accesso Clerk (vedi
 * src/renderer/src/mobile-harness.jsx).
 *
 * Uso:
 *   npm run dev:web            (in un terminale)
 *   npm run test:mobile        (in un altro)
 *
 * Cosa fa fallire il test:
 *   - scorrimento orizzontale a una qualsiasi delle larghezze provate;
 *   - un comando raggiungibile più basso di 44px (WCAG 2.2, 2.5.8);
 *   - un campo con testo sotto i 16px, che su iOS fa ingrandire la pagina al
 *     primo tocco e non la riporta più indietro;
 *   - il binario laterale che occupa spazio invece di farsi cassetto;
 *   - il cassetto che non si apre, non si chiude, o resta tabulabile da chiuso;
 *   - le tabelle che restano tabelle invece di diventare schede.
 */

import { spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { basename, join, resolve, sep } from "node:path";

// Le opzioni vanno scartate prima di cercare l'indirizzo, altrimenti
// `node scripts/mobile-smoke.mjs --screenshots` prova ad aprire "--screenshots"
// come se fosse un URL.
const positional = process.argv.slice(2).filter((argument) => !argument.startsWith("--"));
const baseUrl = new URL(positional[0] ?? "http://localhost:5173");
const shouldCaptureScreenshots = process.argv.includes("--screenshots");
const targetUrl = new URL("/mobile-harness.html", baseUrl).toString();

const browserCandidates = [
  process.env.CHROME_PATH,
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
].filter(Boolean);
const browserPath = browserCandidates.find((candidate) => existsSync(candidate));

if (!browserPath) {
  throw new Error("Chrome/Edge non trovato. Imposta CHROME_PATH e riprova.");
}

const port = 9400 + (process.pid % 500);
const artifactsDir = resolve(".mobile-smoke");
mkdirSync(artifactsDir, { recursive: true });
const profileDir = mkdtempSync(join(artifactsDir, "chrome-profile-"));
const screenshots = {
  list: join(artifactsDir, "soci-390.png"),
  drawer: join(artifactsDir, "cassetto-390.png"),
  modal: join(artifactsDir, "modale-390.png"),
};

const browser = spawn(
  browserPath,
  [
    "--headless=new",
    "--disable-gpu",
    "--disable-crash-reporter",
    "--disable-extensions",
    "--no-first-run",
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profileDir}`,
    "about:blank",
  ],
  { stdio: "ignore", windowsHide: true },
);

const sleep = (milliseconds) => new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));

const waitForBrowser = async () => {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json`);
      if (response.ok) return response.json();
    } catch {
      // Il processo headless può impiegare qualche istante ad aprire la porta.
    }
    await sleep(250);
  }
  throw new Error("Il browser headless non ha aperto il protocollo di debug.");
};

const connect = async (webSocketDebuggerUrl) => {
  const socket = new WebSocket(webSocketDebuggerUrl);
  await new Promise((resolvePromise, rejectPromise) => {
    socket.addEventListener("open", resolvePromise, { once: true });
    socket.addEventListener("error", rejectPromise, { once: true });
  });

  let requestId = 0;
  const pending = new Map();
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    const request = pending.get(message.id);
    if (!request) return;
    pending.delete(message.id);
    clearTimeout(request.timer);
    if (message.error) request.reject(new Error(message.error.message));
    else request.resolve(message.result);
  });

  const send = (method, params = {}) =>
    new Promise((resolvePromise, rejectPromise) => {
      requestId += 1;
      const currentId = requestId;
      const timer = setTimeout(() => {
        pending.delete(currentId);
        rejectPromise(new Error(`Timeout DevTools durante ${method}`));
      }, 10000);
      pending.set(currentId, { resolve: resolvePromise, reject: rejectPromise, timer });
      socket.send(JSON.stringify({ id: requestId, method, params }));
    });

  return { socket, send };
};

/**
 * Misura la pagina così com'è, senza toccarla.
 *
 * Un comando conta come raggiungibile solo se è davvero visibile: il cassetto
 * chiuso vive a translateX(-100%) con visibility:hidden, e misurarne i bottoni
 * darebbe una falsa violazione dei 44px su una cosa che nessuno può premere.
 */
const measureExpression = `(() => {
  const reachable = (element) => {
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    if (rect.width === 0 || rect.height === 0) return false;
    if (style.visibility === "hidden" || style.display === "none") return false;
    // Fuori dal viewport a sinistra: è il cassetto chiuso, non un bersaglio.
    if (rect.right <= 0 || rect.left >= innerWidth) return false;
    let node = element;
    while (node) {
      if (getComputedStyle(node).visibility === "hidden") return false;
      node = node.parentElement;
    }
    return true;
  };

  const controls = [...document.querySelectorAll(
    "button, a.btn, [role='button'], input:not([type='hidden']):not([type='checkbox']):not([type='radio']), select, textarea"
  )].filter(reachable);

  const fields = [...document.querySelectorAll("input:not([type='hidden']), select, textarea")]
    .filter(reachable);

  const smallest = controls
    .map((element) => ({
      tag: element.tagName.toLowerCase(),
      label: (element.getAttribute("aria-label") || element.textContent || "").trim().slice(0, 40),
      height: Math.round(element.getBoundingClientRect().height),
      width: Math.round(element.getBoundingClientRect().width),
    }))
    .sort((a, b) => a.height - b.height)[0] ?? null;

  const smallestFont = fields
    .map((element) => ({
      name: element.getAttribute("placeholder") || element.tagName.toLowerCase(),
      size: Math.round(parseFloat(getComputedStyle(element).fontSize)),
    }))
    .sort((a, b) => a.size - b.size)[0] ?? null;

  const sidebar = document.querySelector(".jl-sidebar");
  const sidebarRect = sidebar?.getBoundingClientRect();
  const sidebarStyle = sidebar ? getComputedStyle(sidebar) : null;

  const table = document.querySelector(".jl-content table");
  const firstCell = document.querySelector(".jl-content tbody td");

  // Il numero di elementi che sforano a destra: dice *cosa* allarga la pagina,
  // non solo che qualcosa lo fa.
  const overflowing = [...document.querySelectorAll(".jl-root *")]
    .filter((element) => element.getBoundingClientRect().right > document.documentElement.clientWidth + 1)
    .slice(0, 5)
    .map((element) => ({
      tag: element.tagName.toLowerCase(),
      cls: (element.getAttribute("class") || "").slice(0, 60),
      right: Math.round(element.getBoundingClientRect().right),
    }));

  return {
    innerWidth,
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    controlCount: controls.length,
    smallestControl: smallest,
    smallestFieldFont: smallestFont,
    sidebar: sidebar ? {
      position: sidebarStyle.position,
      visibility: sidebarStyle.visibility,
      left: Math.round(sidebarRect.left),
      width: Math.round(sidebarRect.width),
    } : null,
    tableIsCards: Boolean(table?.classList.contains("jl-cards-sm")),
    tableDisplay: table ? getComputedStyle(table).display : null,
    firstCellLabel: firstCell?.getAttribute("data-label") ?? null,
    overflowing,
  };
})()`;

const drawerExpression = `(() => {
  const burger = document.querySelector(".top-header-burger");
  const sidebar = document.querySelector(".jl-sidebar");
  const backdrop = document.querySelector(".jl-sidebar-backdrop");
  const burgerVisible = burger ? getComputedStyle(burger).display !== "none" : false;

  burger?.click();
  return new Promise((done) => setTimeout(() => {
    const style = getComputedStyle(sidebar);
    const rect = sidebar.getBoundingClientRect();
    const link = sidebar.querySelector(".jl-nav-link");
    done({
      burgerVisible,
      burgerExpanded: burger?.getAttribute("aria-expanded"),
      openVisibility: style.visibility,
      openLeft: Math.round(rect.left),
      openWidth: Math.round(rect.width),
      fitsViewport: Math.round(rect.width) <= innerWidth,
      backdropVisible: backdrop ? getComputedStyle(backdrop).visibility : null,
      navLinkHeight: link ? Math.round(link.getBoundingClientRect().height) : 0,
      focusInsideDrawer: sidebar.contains(document.activeElement),
    });
  }, 420));
})()`;

const closeExpression = `(() => {
  const sidebar = document.querySelector(".jl-sidebar");
  document.querySelector(".jl-sidebar-backdrop")?.click();
  return new Promise((done) => setTimeout(() => {
    const style = getComputedStyle(sidebar);
    done({
      closedVisibility: style.visibility,
      closedLeft: Math.round(sidebar.getBoundingClientRect().left),
      bodyOverflow: document.body.style.overflow || "(vuoto)",
    });
  }, 450));
})()`;

let connection;
try {
  const tabs = await waitForBrowser();
  // Mai tabs[0]: con estensioni installate via policy il primo target può
  // essere una background_page e la navigazione finirebbe nel vuoto.
  const pageTab = tabs.find((tab) => tab.type === "page");
  if (!pageTab) throw new Error("Nessun target di tipo page nel browser headless.");
  connection = await connect(pageTab.webSocketDebuggerUrl);
  const { send } = connection;
  await send("Page.enable");
  await send("Runtime.enable");

  const waitForHarness = async () => {
    for (let attempt = 0; attempt < 60; attempt += 1) {
      const result = await send("Runtime.evaluate", {
        expression: 'Boolean(document.querySelector(".jl-sidebar") && document.querySelector("td[data-label]"))',
        returnByValue: true,
      });
      if (result.result.value) return;
      await sleep(200);
    }
    throw new Error("Il gestionale di prova non si è montato o le tabelle non sono pronte.");
  };

  const results = [];
  for (const width of [320, 375, 390, 768, 1280]) {
    await send("Emulation.setDeviceMetricsOverride", {
      width,
      height: width < 640 ? 844 : 1024,
      deviceScaleFactor: 1,
      mobile: width < 640,
      screenWidth: width,
      screenHeight: width < 640 ? 844 : 1024,
    });
    await send("Page.navigate", { url: targetUrl });
    await waitForHarness();
    await sleep(250);
    const evaluation = await send("Runtime.evaluate", {
      expression: measureExpression,
      returnByValue: true,
    });
    results.push({ viewport: width, ...evaluation.result.value });

    if (shouldCaptureScreenshots && width === 390) {
      const shot = await send("Page.captureScreenshot", { format: "png", fromSurface: true });
      writeFileSync(screenshots.list, Buffer.from(shot.data, "base64"));
    }
  }

  // --- Il cassetto, a 390px ---
  await send("Emulation.setDeviceMetricsOverride", {
    width: 390, height: 844, deviceScaleFactor: 1, mobile: true,
    screenWidth: 390, screenHeight: 844,
  });
  await send("Page.navigate", { url: targetUrl });
  await waitForHarness();
  await sleep(250);

  const openEvaluation = await send("Runtime.evaluate", {
    expression: drawerExpression,
    returnByValue: true,
    awaitPromise: true,
  });
  const drawer = openEvaluation.result.value;

  if (shouldCaptureScreenshots) {
    const shot = await send("Page.captureScreenshot", { format: "png", fromSurface: true });
    writeFileSync(screenshots.drawer, Buffer.from(shot.data, "base64"));
  }

  const closeEvaluation = await send("Runtime.evaluate", {
    expression: closeExpression,
    returnByValue: true,
    awaitPromise: true,
  });
  const drawerClosed = closeEvaluation.result.value;

  // --- La modale, a 390px ---
  const modalEvaluation = await send("Runtime.evaluate", {
    expression: `(() => {
      [...document.querySelectorAll("button")]
        .find((b) => b.textContent.includes("Aggiungi socio"))?.click();
      return new Promise((done) => setTimeout(() => {
        const dialog = document.querySelector(".modal-dialog");
        const rect = dialog?.getBoundingClientRect();
        done({
          open: Boolean(dialog),
          fullscreen: Boolean(document.querySelector(".modal-fullscreen-md-down")),
          width: rect ? Math.round(rect.width) : 0,
          fitsViewport: rect ? Math.round(rect.width) <= innerWidth : false,
        });
      }, 500));
    })()`,
    returnByValue: true,
    awaitPromise: true,
  });
  const modal = modalEvaluation.result.value;

  if (shouldCaptureScreenshots) {
    const shot = await send("Page.captureScreenshot", { format: "png", fromSurface: true });
    writeFileSync(screenshots.modal, Buffer.from(shot.data, "base64"));
  }

  // ------------------------------------------------------------------ verdetto
  const failures = [];

  for (const result of results) {
    const label = `${result.viewport}px`;

    if (result.scrollWidth > result.clientWidth || result.bodyScrollWidth > result.clientWidth) {
      const culprits = result.overflowing.map((o) => `${o.tag}.${o.cls}`).join(" | ") || "?";
      failures.push(`${label}: scorrimento orizzontale (${result.scrollWidth} > ${result.clientWidth}) — ${culprits}`);
    }

    // 44px è la soglia del dito, e vale dove si tocca. Sopra i 768px si punta
    // con il mouse e il minimo è quello delle WCAG 2.2 (2.5.8): 24px. Alzare
    // anche il desktop a 44 vorrebbe dire ridisegnare l'applicazione Electron,
    // che non ha questo problema.
    const minimumTarget = result.viewport < 768 ? 44 : 24;
    if (result.smallestControl && result.smallestControl.height < minimumTarget) {
      failures.push(
        `${label}: comando alto ${result.smallestControl.height}px `
        + `("${result.smallestControl.label}" <${result.smallestControl.tag}>), minimo ${minimumTarget}`
      );
    }

    if (result.smallestFieldFont && result.viewport < 768 && result.smallestFieldFont.size < 16) {
      failures.push(
        `${label}: campo "${result.smallestFieldFont.name}" a ${result.smallestFieldFont.size}px — iOS ingrandisce sotto i 16`
      );
    }

    // Sotto i 992px il binario deve essere fuori dal flusso; sopra, dentro.
    if (result.sidebar) {
      const shouldBeDrawer = result.viewport < 992;
      const isDrawer = result.sidebar.position === "fixed";
      if (shouldBeDrawer && !isDrawer) {
        failures.push(`${label}: il binario laterale occupa ancora spazio invece di farsi cassetto`);
      }
      if (!shouldBeDrawer && isDrawer) {
        failures.push(`${label}: il binario laterale è un cassetto anche su schermo largo`);
      }
      if (shouldBeDrawer && result.sidebar.visibility !== "hidden") {
        failures.push(`${label}: il cassetto chiuso resta visibile e tabulabile`);
      }
    } else {
      failures.push(`${label}: binario laterale non trovato`);
    }

    // Tabelle a schede solo sotto i 768px.
    const shouldBeCards = result.viewport < 768;
    if (shouldBeCards && result.tableDisplay !== "block") {
      failures.push(`${label}: la tabella è ancora una tabella (display: ${result.tableDisplay})`);
    }
    if (!shouldBeCards && result.tableDisplay === "block") {
      failures.push(`${label}: la tabella è a schede anche su schermo largo`);
    }
    if (!result.tableIsCards) {
      failures.push(`${label}: ResponsiveTables non ha etichettato la tabella`);
    }
    if (result.firstCellLabel !== "Nome") {
      failures.push(`${label}: prima cella etichettata "${result.firstCellLabel}" invece di "Nome"`);
    }
  }

  if (!drawer.burgerVisible) failures.push("il pulsante del menu non compare a 390px");
  if (drawer.burgerExpanded !== "true") failures.push("aria-expanded non passa a true all'apertura");
  if (drawer.openVisibility !== "visible") failures.push("il cassetto aperto non è visibile");
  if (drawer.openLeft !== 0) failures.push(`il cassetto aperto parte da ${drawer.openLeft}px invece che da 0`);
  if (!drawer.fitsViewport) failures.push(`il cassetto è largo ${drawer.openWidth}px su 390 di schermo`);
  if (drawer.backdropVisible !== "visible") failures.push("lo sfondo del cassetto non compare");
  if (drawer.navLinkHeight < 44) failures.push(`voci del menu alte ${drawer.navLinkHeight}px, minimo 44`);
  if (!drawer.focusInsideDrawer) failures.push("all'apertura il fuoco non entra nel cassetto");

  if (drawerClosed.closedVisibility !== "hidden") failures.push("il cassetto non si chiude toccando lo sfondo");
  if (drawerClosed.bodyOverflow === "hidden") failures.push("dopo la chiusura la pagina resta bloccata");

  if (!modal.open) failures.push("la modale non si apre");
  if (!modal.fullscreen) failures.push("la modale non è a tutto schermo sotto md");
  if (!modal.fitsViewport) failures.push(`la modale è larga ${modal.width}px su 390 di schermo`);

  console.log(JSON.stringify({
    ok: failures.length === 0,
    results,
    drawer: { ...drawer, ...drawerClosed },
    modal,
    screenshots: shouldCaptureScreenshots ? screenshots : "disattivati (usa --screenshots)",
    failures,
  }, null, 2));

  if (failures.length) process.exitCode = 1;
  await Promise.race([
    send("Browser.close").catch(() => undefined),
    sleep(500),
  ]);
} finally {
  connection?.socket.close();
  if (!browser.killed) browser.kill();
  const temporaryRoot = `${artifactsDir}${sep}`;
  const resolvedProfile = resolve(profileDir);
  if (resolvedProfile.startsWith(temporaryRoot) && basename(resolvedProfile).startsWith("chrome-profile-")) {
    await sleep(250);
    try {
      rmSync(resolvedProfile, { recursive: true, force: true });
    } catch {
      // Chrome può trattenere un file per pochi istanti: il profilo resta in .mobile-smoke.
    }
  }
}
