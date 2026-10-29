/**
 * Chi vede cosa.
 *
 * Questa mappa è il gemello lato interfaccia della matrice dei permessi in
 * `CloudSecurityConfig`. Le due vanno tenute allineate, con una precisazione
 * che conta: **qui si nasconde, là si vieta**. Nascondere una voce di menu è
 * cortesia verso l'utente, non sicurezza — chi volesse aggirarla dovrebbe
 * comunque passare dal backend, che risponde 403.
 *
 * I nomi dei ruoli sono gli stessi del sito (sito/src/data/admin.ts) e dei
 * claim Clerk.
 */

/** Ruoli senza accesso al gestionale: vedono solo la propria posizione. */
export const PERSONAL_ROLES = ['socio', 'volontario'];

/**
 * Voci visibili per ruolo. `'*'` significa tutto.
 *
 * Il criterio non è gerarchico ma funzionale: il referente volontari non è
 * "meno" del presidente, si occupa d'altro. Dare a ciascuno solo ciò che gli
 * serve riduce anche l'esposizione dei dati personali dei beneficiari.
 */
export const ROLE_PAGES = {
  presidente: '*',

  'responsabile-amministrativo': [
    // Le fatture ricevute stanno qui e non altrove: è chi tiene i conti che le
    // registra e le paga.
    'dashboard', 'fiscal', 'expense-documents', 'budget', 'balance', 'projects',
    'members', 'assemblies', 'certificates', 'manual', 'settings'
  ],

  'coordinatore-servizi': [
    'activities', 'events', 'attendance', 'volunteers',
    'projects', 'communications', 'manual'
  ],

  'referente-volontari': [
    'volunteers', 'attendance', 'members', 'certificates', 'manual'
  ],

  // Ruoli di servizio: la propria agenda e poco altro. Non hanno motivo di
  // vedere contabilità, libro soci o assemblee.
  psicologo: ['activities', 'attendance', 'manual'],
  'operatore-alimentare': ['activities', 'attendance', 'inventory', 'manual'],
  'orientatore-servizi': ['activities', 'attendance', 'manual'],
  'facilitatore-digitale': ['activities', 'attendance', 'manual'],

  // Soci e volontari non vedono il gestionale: hanno l'area personale.
  socio: [],
  volontario: []
};

/** Normalizza il ruolo come fa il backend: maiuscole, spazi e underscore. */
export function normalizeRole(role) {
  if (!role) return '';
  return String(role).trim().toLowerCase().replaceAll('_', '-').replaceAll(' ', '-');
}

/** Vero se il ruolo vede solo la propria posizione. */
export function isPersonalOnly(role) {
  return PERSONAL_ROLES.includes(normalizeRole(role));
}

/**
 * Risolve il ruolo applicativo dai dati Clerk.
 *
 * I session token Clerk v2 espongono il ruolo dell'organizzazione attiva nella
 * forma compatta `o.rol`. I ruoli predefiniti Clerk vengono tradotti negli
 * equivalenti del gestionale; un ruolo personalizzato mantiene il proprio
 * slug. Il ruolo organizzativo prevale; publicMetadata resta soltanto come
 * compatibilità per installazioni senza Organizations.
 */
export function clerkApplicationRole(user, sessionClaims) {
  if (sessionClaims?.o && Object.prototype.hasOwnProperty.call(sessionClaims.o, 'rol')) {
    const organizationRole = typeof sessionClaims.o.rol === 'string' ? normalizeRole(sessionClaims.o.rol) : '';
    if (organizationRole === 'admin') return 'presidente';
    if (organizationRole === 'member') return 'socio';
    return organizationRole || 'socio';
  }

  const explicitRole =
    user?.publicMetadata?.role ??
    sessionClaims?.role ??
    sessionClaims?.public_metadata?.role;
  return explicitRole ? normalizeRole(explicitRole) : 'socio';
}

/**
 * Filtra le voci di menu per il ruolo.
 *
 * Un ruolo sconosciuto non riceve niente: è la stessa scelta del backend, dove
 * un ruolo non riconosciuto nel token non concede authority. Meglio
 * un'interfaccia vuota che una che mostra ciò che non dovrebbe.
 */
export function pagesForRole(pages, role) {
  const normalized = normalizeRole(role);
  const allowed = ROLE_PAGES[normalized];

  if (allowed === '*') return pages;
  if (!allowed) return [];
  return pages.filter((page) => allowed.includes(page.id));
}
