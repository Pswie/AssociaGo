/**
 * L'esercizio: quale anno si sta guardando, e cosa appartiene a quell'anno.
 *
 * ## La regola, che non è «datato nel {{anno}}»
 *
 * Trattare tutto per data di creazione sarebbe sbagliato in modo silenzioso:
 * una tessera aperta nel 2026 sparirebbe passando al 2027, e un progetto
 * cominciato l'anno scorso e ancora in corso non comparirebbe da nessuna parte.
 *
 * Le cose sono di due specie, e vanno trattate diversamente:
 *
 *  * **puntuali** — un movimento, una fattura, un buono consegnato, un rimborso.
 *    Sono accadute in un giorno e basta. Appartengono a quell'anno e a nessun
 *    altro.
 *
 *  * **con un periodo** — una tessera, un volontario iscritto al registro, un
 *    progetto, un'attività, una polizza, una presa in carico. Hanno un inizio e
 *    a volte una fine. Appartengono a **ogni anno che attraversano**.
 *
 * Da cui il criterio: *ciò che continua non si archivia, ciò che è finito sì*.
 * Una tessera senza data di cessazione è in essere, quindi compare anche negli
 * anni futuri; quando cessa, resta visibile solo negli anni che ha coperto.
 *
 * ## Perché non si sposta niente
 *
 * «Archiviare» qui non vuol dire trasferire righe in un'altra tabella. I dati
 * restano dove sono — si tengono finché una persona non decide di toglierli — e
 * l'anno è una lente con cui si guardano. Spostare righe significherebbe avere
 * due posti dove cercare la stessa cosa, e prima o poi trovarne solo uno.
 */

const CHIAVE = 'associago.esercizio';

/** L'anno solare corrente: il valore predefinito quando non se n'è scelto uno. */
export const annoCorrente = () => new Date().getFullYear();

/**
 * L'anno scelto, ricordato fra una sessione e l'altra.
 *
 * Se il valore salvato non è un anno plausibile si torna a quello corrente
 * invece di fidarsi: `localStorage` lo può aver scritto una versione precedente
 * del programma, o una mano.
 */
export const leggiAnnoSalvato = () => {
  try {
    const salvato = Number(localStorage.getItem(CHIAVE));
    if (Number.isInteger(salvato) && salvato >= 2000 && salvato <= 2100) return salvato;
  } catch {
    // localStorage può non esserci (contesto ristretto): non è un errore.
  }
  return annoCorrente();
};

export const salvaAnno = (anno) => {
  try {
    localStorage.setItem(CHIAVE, String(anno));
  } catch {
    // Se non si può salvare, la scelta vale per questa sessione. Amen.
  }
};

/**
 * Gli anni fra cui si può scegliere.
 *
 * Dal più antico dato conosciuto fino all'anno prossimo — l'anno prossimo
 * perché tessere e progetti si aprono spesso in anticipo, e non poterli vedere
 * finché non scocca il primo gennaio sarebbe una limitazione senza motivo.
 *
 * @param annoPiuVecchio il primo anno con dei dati; se ignoto si parte da tre
 *        anni fa, che copre l'uso normale senza allungare l'elenco.
 */
export const anniDisponibili = (annoPiuVecchio) => {
  const fine = annoCorrente() + 1;
  const inizio = Number.isInteger(annoPiuVecchio) && annoPiuVecchio > 2000
    ? annoPiuVecchio
    : annoCorrente() - 3;
  const anni = [];
  for (let a = fine; a >= inizio; a -= 1) anni.push(a);
  return anni;
};

/** Il primo e l'ultimo giorno dell'anno, in forma ISO per le API. */
export const inizioAnno = (anno) => `${anno}-01-01`;
export const fineAnno = (anno) => `${anno}-12-31`;

const anno = (valore) => {
  if (!valore) return null;
  const d = valore instanceof Date ? valore : new Date(valore);
  return Number.isNaN(d.getTime()) ? null : d.getFullYear();
};

/**
 * Per le cose **puntuali**: appartiene all'anno indicato?
 *
 * Una data assente conta come appartenente: è meglio mostrare una riga senza
 * data che farla sparire da tutti gli anni, dove nessuno la troverebbe più.
 */
export const nellAnno = (data, annoScelto) => {
  const a = anno(data);
  return a === null ? true : a === annoScelto;
};

/**
 * Per le cose **con un periodo**: era in essere nell'anno indicato?
 *
 * Vero se il periodo si sovrappone anche di un solo giorno all'anno scelto.
 * Una fine assente significa «ancora in corso», quindi vale per l'anno scelto e
 * per tutti quelli successivi: è così che una tessera aperta non sparisce
 * cambiando anno.
 *
 * Un inizio assente conta come «da sempre», per la stessa ragione per cui una
 * data mancante non fa sparire una riga.
 */
export const attivoNellAnno = (inizio, fine, annoScelto) => {
  const dal = anno(inizio);
  const al = anno(fine);
  if (dal !== null && dal > annoScelto) return false;   // comincia dopo
  if (al !== null && al < annoScelto) return false;     // finita prima
  return true;
};

/** Vero se la cosa non è ancora finita: serve a distinguerla nell'elenco. */
export const ancoraInCorso = (fine) => {
  const a = anno(fine);
  return a === null || a >= annoCorrente();
};

/**
 * Filtra un elenco di cose puntuali.
 *
 * @param campoData nome del campo con la data, es. `'date'`, `'documentDate'`.
 */
export const soloDellAnno = (voci, campoData, annoScelto) =>
  (voci ?? []).filter((v) => nellAnno(v?.[campoData], annoScelto));

/**
 * Filtra un elenco di cose con un periodo.
 *
 * @param campoInizio es. `'startDate'`, `'startedAt'`, `'joinDate'`.
 * @param campoFine   es. `'endDate'`, `'endedAt'`, `'expirationDate'`.
 */
export const soloInEssereNellAnno = (voci, campoInizio, campoFine, annoScelto) =>
  (voci ?? []).filter((v) => attivoNellAnno(v?.[campoInizio], v?.[campoFine], annoScelto));
