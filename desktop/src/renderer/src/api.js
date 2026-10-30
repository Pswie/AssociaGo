/**
 * AssociaGo API Client
 *
 * Gestisce la comunicazione con il backend Java (Spring Boot).
 * Mappa completa dei Controller REST v1.
 *
 * @module associago
 */

// ========================================
// Configuration & Core
// ========================================

let API_BASE_URL = null;
let _initInFlight = null;
let CURRENT_ASSOCIATION_ID = null;
const DEFAULT_PORT = 8080;

const STORAGE_KEY_AUTOLOGIN = 'associago_autologin_v1';
const STORAGE_KEY_ASSOCIATIONS = 'associago_associations_list';
const STORAGE_KEY_LANGUAGE = 'associago_language';
const STORAGE_KEY_THEME = 'associago_theme';

/**
 * Base URL configurata a build time. Sul web è l'unica sorgente possibile:
 * non esiste un processo Electron che scopra la porta del backend, perché il
 * backend sta su un altro host. Nel desktop resta vuota e vince la scoperta
 * via `window.api`.
 */
const CONFIGURED_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || '').replace(/\/+$/, '');

/** True quando giriamo nel renderer Electron, false nel browser. */
const IS_DESKTOP = typeof window !== 'undefined' && Boolean(window.api);

/**
 * Funzione che restituisce il token di accesso corrente, registrata dal gate
 * Clerk sul web. È una funzione e non una stringa perché i token Clerk hanno
 * vita breve: va chiesto quello fresco a ogni richiesta, non memorizzato.
 * Nel desktop resta null e nessun header Authorization viene aggiunto.
 */
let TOKEN_PROVIDER = null;

async function currentAuthToken() {
    if (!TOKEN_PROVIDER) return null;
    try {
        return await TOKEN_PROVIDER();
    } catch (error) {
        console.warn('[API] Token non disponibile:', error);
        return null;
    }
}

async function initializeApi() {
    if (API_BASE_URL) return API_BASE_URL;
    if (_initInFlight) return _initInFlight;

    _initInFlight = (async () => {
        // Web: l'indirizzo del backend è noto a build time e non si scopre.
        if (CONFIGURED_BASE_URL) {
            API_BASE_URL = CONFIGURED_BASE_URL;
            return API_BASE_URL;
        }

        try {
            if (window.api && window.api.getBackendInfo) {
                const info = await window.api.getBackendInfo();
                if (info?.apiBaseUrl) {
                    API_BASE_URL = info.apiBaseUrl;
                    return API_BASE_URL;
                }
                if (info?.host && info?.port) {
                    const protocol = info.protocol || 'http';
                    API_BASE_URL = `${protocol}://${info.host}:${info.port}/api`;
                    return API_BASE_URL;
                }
            }
        } catch (e) {
            console.warn('[API] BackendInfo Electron non disponibile:', e);
        }

        try {
            if (window.api && window.api.getBackendPort) {
                const port = await window.api.getBackendPort();
                if (port) {
                    API_BASE_URL = `http://127.0.0.1:${port}/api`;
                    return API_BASE_URL;
                }
            }
        } catch (e) {
            console.warn('[API] BackendPort Electron non disponibile:', e);
        }

        if (!IS_DESKTOP) {
            // Sul web arrivare qui significa build senza VITE_API_BASE_URL: meglio
            // un errore esplicito che centinaia di fetch verso il localhost di chi
            // sta guardando la pagina.
            throw new Error(
                'VITE_API_BASE_URL non configurata: la build web non sa dove sia il backend.'
            );
        }

        API_BASE_URL = `http://127.0.0.1:${DEFAULT_PORT}/api`;
        return API_BASE_URL;
    })();

    try {
        return await _initInFlight;
    } finally {
        _initInFlight = null;
    }
}

function shouldRetryNetworkError(error, options) {
    const method = String(options.method || 'GET').toUpperCase();
    return error instanceof TypeError && ['GET', 'HEAD', 'OPTIONS'].includes(method);
}

async function performFetch(endpoint, options = {}) {
    const baseUrl = await getApiUrl();
    const url = `${baseUrl}${endpoint}`;
    const headers = { 'Accept': 'application/json', ...options.headers };

    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
        headers['Content-Type'] = 'application/json';
    }
    if (CURRENT_ASSOCIATION_ID) headers['X-Association-Id'] = CURRENT_ASSOCIATION_ID;

    const token = await currentAuthToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;

    // Sul web l'identità viaggia nel Bearer token, non nei cookie: chiedere le
    // credenziali costringerebbe il backend ad allargare la politica CORS.
    const config = { ...options, headers, credentials: IS_DESKTOP && !token ? 'include' : 'omit' };
    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        config.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, config);
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        const error = new Error(errorData.message || errorData.error || (typeof errorData === 'string' ? errorData : `HTTP ${response.status}`));
        error.status = response.status;
        throw error;
    }
    if (response.status === 204) return null;

    const contentType = response.headers.get("content-type");
    if (options.responseType === 'blob' || (contentType && (contentType.includes("pdf") || contentType.includes("image") || contentType.includes("octet-stream")))) {
        return response.blob();
    }

    const text = await response.text();
    if (!text) return null;
    return contentType?.includes('json') ? JSON.parse(text) : text;
}

async function apiRequest(endpoint, options = {}) {
    try {
        return await performFetch(endpoint, options);
    } catch (error) {
        if (shouldRetryNetworkError(error, options)) {
            API_BASE_URL = null;
            try {
                return await performFetch(endpoint, options);
            } catch (retryError) {
                console.error('[API] Error after retry:', retryError);
                throw retryError;
            }
        }

        console.error('[API] Error:', error);
        throw error;
    }
}

async function getApiUrl() {
    if (!API_BASE_URL) await initializeApi();
    return API_BASE_URL;
}

function requireActorId(value) {
    const id = Number(value);
    if (!Number.isSafeInteger(id) || id <= 0) {
        throw new Error("Collega questo accesso all'anagrafica dell'associazione prima di inviare comunicazioni o approvare documenti. Chiedi al referente di verificare la tua email.");
    }
    return id;
}

// ========================================
// DOMAIN MODULES
// ========================================

const activities = {
    // Un corso senza data di fine è ancora in corso e compare anche negli
    // anni successivi a quello in cui è cominciato.
    getAll: (assocId, anno) => apiRequest(
        `/v1/activities?associationId=${assocId || CURRENT_ASSOCIATION_ID}${anno ? `&anno=${anno}` : ''}`,
    ),
    getById: (id) => apiRequest(`/v1/activities/${id}`),
    create: (data) => apiRequest('/v1/activities', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/activities/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/activities/${id}`, { method: 'DELETE' }),

    // Advanced Stats
    getDetails: (id) => apiRequest(`/v1/activities/${id}/details`),
    getFinancialSummary: (id) => apiRequest(`/v1/activities/${id}/financial-summary`),

    // Sub-resources: Costs
    getCosts: (id) => apiRequest(`/v1/activities/${id}/costs`),
    addCost: (id, data) => apiRequest(`/v1/activities/${id}/costs`, { method: 'POST', body: data }),
    updateCost: (costId, data) => apiRequest(`/v1/activities/costs/${costId}`, { method: 'PUT', body: data }),
    deleteCost: (costId) => apiRequest(`/v1/activities/costs/${costId}`, { method: 'DELETE' }),

    // Sub-resources: Instructors
    getInstructors: (id) => apiRequest(`/v1/activities/${id}/instructors`),
    addInstructor: (id, data) => apiRequest(`/v1/activities/${id}/instructors`, { method: 'POST', body: data }),
    updateInstructor: (instId, data) => apiRequest(`/v1/activities/instructors/${instId}`, { method: 'PUT', body: data }),
    deleteInstructor: (instId) => apiRequest(`/v1/activities/instructors/${instId}`, { method: 'DELETE' }),

    // Sub-resources: Schedules
    getSchedules: (id) => apiRequest(`/v1/activities/${id}/schedules`),
    addSchedule: (id, data) => apiRequest(`/v1/activities/${id}/schedules`, { method: 'POST', body: data }),
    updateSchedule: (schId, data) => apiRequest(`/v1/activities/schedules/${schId}`, { method: 'PUT', body: data }),
    deleteSchedule: (schId) => apiRequest(`/v1/activities/schedules/${schId}`, { method: 'DELETE' }),

    // Sub-resources: Participants
    getParticipants: (id) => apiRequest(`/v1/activities/${id}/participants`),
    getParticipantCandidates: (id) => apiRequest(`/v1/activities/${id}/participant-candidates`),
    addParticipant: (id, data) => apiRequest(`/v1/activities/${id}/participants`, { method: 'POST', body: data }),
    removeParticipant: (partId) => apiRequest(`/v1/activities/participants/${partId}`, { method: 'DELETE' }),
};

const events = {
    // Un evento ha un periodo: quello a cavallo di capodanno compare in
    // entrambi gli esercizi. Il filtro lo applica il server.
    getAll: (anno) => apiRequest(`/v1/events${anno ? `?anno=${anno}` : ''}`),
    getById: (id) => apiRequest(`/v1/events/${id}`),
    create: (data) => apiRequest('/v1/events', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/events/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/events/${id}`, { method: 'DELETE' }),

    // Stats
    getSummary: (id) => apiRequest(`/v1/events/${id}/summary`),
    getGlobalStats: () => apiRequest('/v1/events/stats/global'),

    // Participants
    getParticipants: (id) => apiRequest(`/v1/events/${id}/participants`),
    addParticipant: (id, data) => apiRequest(`/v1/events/${id}/participants`, { method: 'POST', body: data }),
    removeParticipant: (partId) => apiRequest(`/v1/events/participants/${partId}`, { method: 'DELETE' }),
};

const assemblies = {
    // Un'assemblea si tiene in un giorno: appartiene a quell'esercizio. Il
    // filtro sta sul server, non qui.
    getAll: (assocId, anno) => {
        const q = new URLSearchParams();
        if (assocId ?? CURRENT_ASSOCIATION_ID) q.set('associationId', assocId ?? CURRENT_ASSOCIATION_ID);
        if (anno) q.set('anno', anno);
        const query = q.toString();
        return apiRequest(`/v1/assemblies${query ? `?${query}` : ''}`);
    },
    getById: (id) => apiRequest(`/v1/assemblies/${id}`),
    create: (data) => apiRequest('/v1/assemblies', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/assemblies/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/assemblies/${id}`, { method: 'DELETE' }),

    // Details & Live
    getDetails: (id) => apiRequest(`/v1/assemblies/${id}/details`),

    // Sub-resources
    getParticipants: (id) => apiRequest(`/v1/assemblies/${id}/participants`),
    addParticipant: (id, data) => apiRequest(`/v1/assemblies/${id}/participants`, { method: 'POST', body: data }),
    removeParticipant: (partId) => apiRequest(`/v1/assemblies/participants/${partId}`, { method: 'DELETE' }),

    getMotions: (id) => apiRequest(`/v1/assemblies/${id}/motions`),
    addMotion: (id, data) => apiRequest(`/v1/assemblies/${id}/motions`, { method: 'POST', body: data }),
    deleteMotion: (mId) => apiRequest(`/v1/assemblies/motions/${mId}`, { method: 'DELETE' }),

    getDocuments: (id) => apiRequest(`/v1/assemblies/${id}/documents`),
    addDocument: (id, data) => apiRequest(`/v1/assemblies/${id}/documents`, { method: 'POST', body: data }),
    deleteDocument: (docId) => apiRequest(`/v1/assemblies/documents/${docId}`, { method: 'DELETE' }),

    castVote: (motionId, data) => apiRequest(`/v1/assemblies/motions/${motionId}/votes`, { method: 'POST', body: data }),
    getVotes: (motionId) => apiRequest(`/v1/assemblies/motions/${motionId}/votes`),
};

const inventory = {
    // Un oggetto si possiede dall'acquisto in poi: compare in quell'anno e in
    // tutti i successivi, non solo nell'anno in cui è stato comprato.
    getAll: (anno) => apiRequest(`/v1/inventory${anno ? `?anno=${anno}` : ''}`),
    getById: (id) => apiRequest(`/v1/inventory/${id}`),
    create: (data) => apiRequest('/v1/inventory', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/inventory/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/inventory/${id}`, { method: 'DELETE' }),

    // Loans
    getLoansByItem: (id) => apiRequest(`/v1/inventory/${id}/loans`),
    createLoan: (data) => apiRequest('/v1/inventory/loans', { method: 'POST', body: data }),
    returnLoan: (loanId) => apiRequest(`/v1/inventory/loans/${loanId}/return`, { method: 'PUT' }),
};

const volunteers = {
    getAll: (filters = {}) => apiRequest(`/v1/volunteers?${new URLSearchParams(filters)}`),
    getById: (id) => apiRequest(`/v1/volunteers/${id}`),
    create: (data) => apiRequest('/v1/volunteers', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/volunteers/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/volunteers/${id}`, { method: 'DELETE' }),

    // Shifts
    getShiftsByVolunteer: (id) => apiRequest(`/v1/volunteers/${id}/shifts`),
    createShift: (data) => apiRequest('/v1/volunteers/shifts', { method: 'POST', body: data }),
    updateShift: (id, data) => apiRequest(`/v1/volunteers/shifts/${id}`, { method: 'PUT', body: data }),

    // Expenses
    getExpensesByVolunteer: (id) => apiRequest(`/v1/volunteers/${id}/expenses`),
    createExpense: (data) => apiRequest('/v1/volunteers/expenses', { method: 'POST', body: data }),
    updateExpenseStatus: (id, status, assocId = CURRENT_ASSOCIATION_ID) => apiRequest(`/v1/volunteers/expenses/${id}/status?status=${encodeURIComponent(status)}${assocId ? '&associationId=' + assocId : ''}`, { method: 'PUT' }),
};

const members = {
    getAll: () => apiRequest('/v1/members'),
    getById: (id) => apiRequest(`/v1/members/${id}`),
    create: (data) => apiRequest('/v1/members', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/members/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/members/${id}`, { method: 'DELETE' }),

    // Utils
    calculateFiscalCode: (data) => apiRequest('/v1/members/calculate-fiscal-code', { method: 'POST', body: data }),

    // Duplicates
    checkDuplicates: (email, fiscalCode) => {
        const params = new URLSearchParams();
        if (email) params.append('email', email);
        if (fiscalCode) params.append('fiscalCode', fiscalCode);
        return apiRequest(`/v1/members/check-duplicates?${params}`);
    },

    // Consents
    getConsents: (memberId, assocId) => apiRequest(`/v1/members/${memberId}/consents?associationId=${assocId}`),
    grantConsent: (memberId, assocId, consentType, lawfulBasis) =>
        apiRequest(`/v1/members/${memberId}/consents?associationId=${assocId}&consentType=${consentType}${lawfulBasis ? '&lawfulBasis=' + lawfulBasis : ''}`, { method: 'POST' }),
    revokeConsent: (consentId) => apiRequest(`/v1/members/consents/${consentId}/revoke`, { method: 'POST' }),
};

const users = {
    create: (data) => apiRequest('/v1/users', { method: 'POST', body: data }),
    getAll: () => apiRequest('/v1/users'),
    update: (id, data) => apiRequest(`/v1/users/${id}`, { method: 'PUT', body: data }),
};

const memberships = {
    // Una tessera aperta compare anche negli anni successivi: la regola
    // sta nella query, non qui.
    getByAssociation: (assocId, anno) => apiRequest(
        `/v1/memberships/association/${assocId || CURRENT_ASSOCIATION_ID}${anno ? `?anno=${anno}` : ''}`,
    ),
    getById: (id) => apiRequest(`/v1/memberships/${id}`),
    create: (data) => apiRequest('/v1/memberships', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/memberships/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/memberships/${id}`, { method: 'DELETE' }),
    renew: (id, newExpirationDate) => apiRequest(`/v1/memberships/${id}/renew?newExpirationDate=${newExpirationDate}`, { method: 'POST' }),
};

const reports = {
    downloadFinancialReport: (year) => apiRequest(`/v1/reports/finance/year/${year || new Date().getFullYear()}`),
    downloadComparisonReport: (year1, year2) => apiRequest(`/v1/reports/finance/comparison?year1=${year1}&year2=${year2}`),
    downloadActivityReport: (id) => apiRequest(`/v1/reports/activities/${id}`),
    downloadAssemblyMinutes: (id) => apiRequest(`/v1/reports/assemblies/${id}/minutes`),
    downloadTransactionReceipt: (id) => apiRequest(`/v1/reports/finance/transactions/${id}/receipt`),
    downloadMembershipCard: (id) => apiRequest(`/v1/reports/members/${id}/card`),
};

const dashboard = {
    // L'anno sta sul server perché qui tornano somme, non righe: un totale
    // già calcolato non si filtra dopo.
    getStats: (assocId, anno) => apiRequest(
        `/dashboard/stats${anno ? `?anno=${anno}` : ''}`,
        { headers: { 'X-Association-Id': assocId } },
    ),
};

const finance = {
    getAllTransactions: (filters = {}) => {
        const queryParams = new URLSearchParams();
        Object.keys(filters).forEach(key => {
            if (filters[key]) queryParams.append(key, filters[key]);
        });
        return apiRequest(`/v1/finance/transactions?${queryParams.toString()}`);
    },
    createTransaction: (data) => apiRequest('/v1/finance/transactions', { method: 'POST', body: data }),
    updateTransaction: (id, data) => apiRequest(`/v1/finance/transactions/${id}`, { method: 'PUT', body: data }),
    deleteTransaction: (id) => apiRequest(`/v1/finance/transactions/${id}`, { method: 'DELETE' }),
    getYoyComparison: (year) => apiRequest(`/v1/finance/yoy-comparison?year=${year}`),
    getCustomComparison: (year1, year2) => apiRequest(`/v1/finance/comparison?year1=${year1}&year2=${year2}`),

    // Journal Entries
    getJournalEntries: (filters = {}) => {
        const queryParams = new URLSearchParams();
        Object.keys(filters).forEach(key => {
            if (filters[key]) queryParams.append(key, filters[key]);
        });
        return apiRequest(`/v1/finance/journal-entries?${queryParams.toString()}`);
    },
};

// Progetti operativi locali e registro comune: gli ID restano distinti.
const projects = {
    getAll: (assocId = CURRENT_ASSOCIATION_ID) => apiRequest(`/v1/projects?associationId=${encodeURIComponent(assocId)}`),
    getById: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}`),
    create: (data) => apiRequest('/v1/projects', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/projects/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    balance: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/balance`),
    suite: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/suite`),
    candidates: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/candidates`),
    assignments: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/assignments`),
    assign: (id, data) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/assignments`, { method: 'POST', body: data }),
    removeAssignment: (id, assignmentId) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/assignments/${encodeURIComponent(assignmentId)}`, { method: 'DELETE' }),
    shifts: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/shifts`),
    createShift: (id, data) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/shifts`, { method: 'POST', body: data }),
    transactions: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/transactions`),
    availableTransactions: (id) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/transactions/available`),
    attachTransaction: (id, transactionId) => apiRequest(`/v1/projects/${encodeURIComponent(id)}/transactions/${encodeURIComponent(transactionId)}`, { method: 'POST' }),
};

const suiteProjects = {
    status: () => apiRequest('/integrations/v1/status'),
    getAll: () => apiRequest('/integrations/v1/projects'),
    getById: (id) => apiRequest(`/integrations/v1/projects/${encodeURIComponent(id)}`),
    share: (localId) => apiRequest('/integrations/v1/projects/share', { method: 'POST', body: { localId: String(localId) } }),
    link: (id, localId) => apiRequest(`/integrations/v1/projects/${encodeURIComponent(id)}/link`, { method: 'POST', body: { localId: String(localId) } }),
    sync: (id) => apiRequest(`/integrations/v1/projects/${encodeURIComponent(id)}/sync`, { method: 'POST' }),
};

// I buoni dati alle persone. Un buono da 10 euro consegnato a qualcuno è denaro
// uscito: `issue` crea anche il movimento in uscita, `create` scrive solo la
// riga. Sono due cose diverse e vanno tenute distinte.
const coupons = {
    // `anno` filtra sul server: un buono è un fatto puntuale e appartiene
    // all'esercizio in cui il denaro è uscito. Omesso significa «tutti».
    getByAssociation: (assocId, anno) => apiRequest(
        `/v1/coupons/association/${assocId || CURRENT_ASSOCIATION_ID}${anno ? `?anno=${anno}` : ''}`,
    ),
    create: (data) => apiRequest('/v1/coupons', { method: 'POST', body: data }),
    issue: (data, userId) => apiRequest(
        `/v1/coupons/issue${userId ? `?userId=${userId}` : ''}`,
        { method: 'POST', body: data },
    ),
    update: (id, data) => apiRequest(`/v1/coupons/${id}`, { method: 'PUT', body: data }),
    validate: (code, assocId) => apiRequest('/v1/coupons/validate', {
        method: 'POST',
        body: { code, associationId: assocId || CURRENT_ASSOCIATION_ID },
    }),
    delete: (id) => apiRequest(`/v1/coupons/${id}`, { method: 'DELETE' }),
};

// Le fatture che l'associazione RICEVE.
//
// Il backend esisteva già per intero — elenco, scadute, importazione della
// fattura elettronica XML — e non era esposto al client da nessuna parte: la
// funzione c'era e nessuno poteva usarla.
const expenseDocuments = {
    // Una fattura appartiene all'anno della sua data: il filtro è del server.
    list: (assocId, anno) => apiRequest(
        `/v1/expense-documents?associationId=${assocId || CURRENT_ASSOCIATION_ID}${anno ? `&anno=${anno}` : ''}`,
    ),
    overdue: (assocId) => apiRequest(`/v1/expense-documents/overdue?associationId=${assocId || CURRENT_ASSOCIATION_ID}`),
    getById: (id) => apiRequest(`/v1/expense-documents/${id}`),
    create: (data) => apiRequest('/v1/expense-documents', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/expense-documents/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/expense-documents/${id}`, { method: 'DELETE' }),
    // L'XML della fattura elettronica: il server lo legge e ne ricava
    // fornitore, numero, imponibile e IVA. Se il file era già stato importato
    // risponde `alreadyPresent` invece di creare un doppione in prima nota.
    importXml: (file, assocId) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(
            `/v1/expense-documents/import-xml?associationId=${assocId || CURRENT_ASSOCIATION_ID}`,
            { method: 'POST', body: formData },
        );
    },
};

// Le aggiunte su un movimento: IVA, commissione, spese, altro. L'importo si
// dichiara, il totale del movimento lo ricalcola il server — mai il client.
const transactionAdditions = {
    list: (transactionId) => apiRequest(`/v1/transactions/${transactionId}/additions`),
    add: (transactionId, data) => apiRequest(`/v1/transactions/${transactionId}/additions`, {
        method: 'POST',
        body: data,
    }),
    remove: (transactionId, additionId) => apiRequest(
        `/v1/transactions/${transactionId}/additions/${additionId}`,
        { method: 'DELETE' },
    ),
};

// Le polizze dei volontari — art. 18 CTS.
const volunteerInsurance = {
    getByAssociation: (assocId) => apiRequest(`/v1/volunteer-insurance/association/${assocId || CURRENT_ASSOCIATION_ID}`),
    save: (data) => apiRequest('/v1/volunteer-insurance', { method: 'POST', body: data }),
    // Il giorno è un parametro: dopo un infortunio la domanda riguarda sempre
    // una data passata.
    coverage: (volunteerId, giorno, assocId) => apiRequest(
        `/v1/volunteer-insurance/coverage?associationId=${assocId || CURRENT_ASSOCIATION_ID}`
        + `&volunteerId=${volunteerId}${giorno ? `&giorno=${giorno}` : ''}`,
    ),
    uncovered: (assocId, giorno) => apiRequest(
        `/v1/volunteer-insurance/uncovered?associationId=${assocId || CURRENT_ASSOCIATION_ID}`
        + `${giorno ? `&giorno=${giorno}` : ''}`,
    ),
    expiring: (assocId, giorni = 60) => apiRequest(
        `/v1/volunteer-insurance/expiring?associationId=${assocId || CURRENT_ASSOCIATION_ID}&giorni=${giorni}`,
    ),
    addCovered: (policyId, volunteerId) => apiRequest(
        `/v1/volunteer-insurance/${policyId}/covered/${volunteerId}`, { method: 'POST' },
    ),
    removeCovered: (policyId, volunteerId) => apiRequest(
        `/v1/volunteer-insurance/${policyId}/covered/${volunteerId}`, { method: 'DELETE' },
    ),
};

// Gli allegati, per qualunque cosa li abbia. Un solo meccanismo al posto delle
// ventiquattro colonne `*_path` che puntavano a un disco che si azzera.
const attachments = {
    list: (ownerType, ownerId) => apiRequest(`/v1/attachments/${ownerType}/${ownerId}`),
    download: (ownerType, ownerId, kind) => apiRequest(`/v1/attachments/${ownerType}/${ownerId}/${kind}/content`, { responseType: 'blob' }),
    upload: (ownerType, ownerId, kind, file, userId) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(
            `/v1/attachments/${ownerType}/${ownerId}/${kind}${userId ? `?userId=${userId}` : ''}`,
            { method: 'POST', body: formData },
        );
    },
    // Non passa da apiRequest perché serve l'URL grezzo per un <a download>.
    // È asincrona perché l'indirizzo del backend si scopre all'avvio — la porta
    // la sceglie il sistema — e prima di allora API_BASE_URL è nulla.
    downloadUrl: async (ownerType, ownerId, kind) => {
        const base = await initializeApi();
        return `${base}/v1/attachments/${ownerType}/${ownerId}/${kind}/content`;
    },
    delete: (ownerType, ownerId, kind) => apiRequest(
        `/v1/attachments/${ownerType}/${ownerId}/${kind}`, { method: 'DELETE' },
    ),
};

const paymentMethods = {
    getAll: (assocId) => apiRequest(`/v1/payment-methods?associationId=${assocId || CURRENT_ASSOCIATION_ID}`),
    getActive: (assocId) => apiRequest(`/v1/payment-methods/active?associationId=${assocId || CURRENT_ASSOCIATION_ID}`),
    create: (data) => apiRequest('/v1/payment-methods', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/payment-methods/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/payment-methods/${id}`, { method: 'DELETE' }),
};

const stats = {
    getGeneral: () => apiRequest('/stats/general'),
    getActivity: (id) => apiRequest(`/stats/activities/${id}`),
    getAssembly: (id) => apiRequest(`/stats/assemblies/${id}`),
    getFinancial: (assocId, year) => apiRequest(`/stats/financial?associationId=${assocId}&year=${year}`),
};

const notifications = {
    getUserNotifications: (userId) => apiRequest(`/v1/notifications/user/${userId}`),
    getUnreadUserNotifications: (userId) => apiRequest(`/v1/notifications/user/${userId}/unread`),
    markAsRead: (id) => apiRequest(`/v1/notifications/${id}/read`, { method: 'PUT' }),
    markAllAsRead: (userId) => apiRequest(`/v1/notifications/user/${userId}/read-all`, { method: 'PUT' }),
};

const budgets = {
    getAll: (assocId) => apiRequest(`/v1/budgets?associationId=${assocId}`),
    getById: (id) => apiRequest(`/v1/budgets/${id}`),
    create: (data) => apiRequest('/v1/budgets', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/budgets/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/budgets/${id}`, { method: 'DELETE' }),
    approve: (id, approvedBy) => apiRequest(`/v1/budgets/${id}/approve?approvedBy=${requireActorId(approvedBy)}`, { method: 'POST' }),
    syncActuals: (id) => apiRequest(`/v1/budgets/${id}/sync-actuals`, { method: 'POST' }),
    getLines: (budgetId) => apiRequest(`/v1/budgets/${budgetId}/lines`),
    addLine: (budgetId, data) => apiRequest(`/v1/budgets/${budgetId}/lines`, { method: 'POST', body: data }),
    updateLine: (lineId, data) => apiRequest(`/v1/budgets/lines/${lineId}`, { method: 'PUT', body: data }),
    deleteLine: (lineId) => apiRequest(`/v1/budgets/lines/${lineId}`, { method: 'DELETE' }),
};

const balances = {
    getAll: (assocId) => apiRequest(`/v1/balances?associationId=${assocId}`),
    getById: (id) => apiRequest(`/v1/balances/${id}`),
    getLines: (id) => apiRequest(`/v1/balances/${id}/lines`),
    compute: (assocId, year) => apiRequest(`/v1/balances/compute?associationId=${assocId}&year=${year}`, { method: 'POST' }),
    approve: (id, approvedBy, signatories) => apiRequest(`/v1/balances/${id}/approve?approvedBy=${requireActorId(approvedBy)}`, { method: 'POST', body: signatories }),
    delete: (id) => apiRequest(`/v1/balances/${id}`, { method: 'DELETE' }),
    downloadPdf: (id) => apiRequest(`/v1/balances/${id}/pdf`),
    checkSigners: (id) => apiRequest(`/v1/balances/${id}/check-signers`),
};

const certificates = {
    getTemplates: (assocId) => apiRequest(`/v1/certificates/templates?associationId=${assocId}`),
    getTemplate: (id) => apiRequest(`/v1/certificates/templates/${id}`),
    createTemplate: (data) => apiRequest('/v1/certificates/templates', { method: 'POST', body: data }),
    updateTemplate: (id, data) => apiRequest(`/v1/certificates/templates/${id}`, { method: 'PUT', body: data }),
    deleteTemplate: (id) => apiRequest(`/v1/certificates/templates/${id}`, { method: 'DELETE' }),
    getIssued: (assocId) => apiRequest(`/v1/certificates?associationId=${assocId}`),
    issue: (params) => apiRequest(`/v1/certificates/issue?${new URLSearchParams(params)}`, { method: 'POST' }),
    batchActivity: (activityId, templateId, assocId) => apiRequest(`/v1/certificates/batch/activity/${activityId}?templateId=${templateId}&associationId=${assocId}`, { method: 'POST' }),
    batchEvent: (eventId, templateId, assocId) => apiRequest(`/v1/certificates/batch/event/${eventId}?templateId=${templateId}&associationId=${assocId}`, { method: 'POST' }),
    downloadPdf: (id) => apiRequest(`/v1/certificates/${id}/pdf`, { raw: true }),
};

const resources = {
    getAll: (assocId) => apiRequest(`/v1/resources?associationId=${assocId}`),
    getById: (id) => apiRequest(`/v1/resources/${id}`),
    create: (data) => apiRequest('/v1/resources', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/resources/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/resources/${id}`, { method: 'DELETE' }),
    getBookings: (assocId) => apiRequest(`/v1/resources/bookings?associationId=${assocId}`),
    getCalendar: (assocId, start, end) => apiRequest(`/v1/resources/bookings/calendar?associationId=${assocId}&start=${start}&end=${end}`),
    createBooking: (data) => apiRequest('/v1/resources/bookings', { method: 'POST', body: data }),
    updateBooking: (id, data) => apiRequest(`/v1/resources/bookings/${id}`, { method: 'PUT', body: data }),
    cancelBooking: (id) => apiRequest(`/v1/resources/bookings/${id}/cancel`, { method: 'POST' }),
    approveBooking: (id, approvedBy) => apiRequest(`/v1/resources/bookings/${id}/approve?approvedBy=${approvedBy}`, { method: 'POST' }),
};

const cashRegisters = {
    getAll: (assocId) => apiRequest(`/v1/cash-registers?associationId=${assocId}`),
    getById: (id) => apiRequest(`/v1/cash-registers/${id}`),
    getOpen: (assocId) => apiRequest(`/v1/cash-registers/open?associationId=${assocId}`),
    open: (assocId, openedBy) => apiRequest(`/v1/cash-registers/open?associationId=${assocId}&openedBy=${openedBy}`, { method: 'POST' }),
    close: (id, closedBy) => apiRequest(`/v1/cash-registers/${id}/close?closedBy=${closedBy}`, { method: 'POST' }),
    getEntries: (registerId) => apiRequest(`/v1/cash-registers/${registerId}/entries`),
    addEntry: (registerId, data) => apiRequest(`/v1/cash-registers/${registerId}/entries`, { method: 'POST', body: data }),
    deleteEntry: (entryId) => apiRequest(`/v1/cash-registers/entries/${entryId}`, { method: 'DELETE' }),
};

const audit = {
    getByAssociation: (assocId, page = 0, size = 50) => apiRequest(`/v1/audit?associationId=${assocId}&page=${page}&size=${size}`),
    getByEntity: (entityType, entityId) => apiRequest(`/v1/audit/entity/${entityType}/${entityId}`),
    getByDateRange: (assocId, from, to) => apiRequest(`/v1/audit/range?associationId=${assocId}&from=${from}&to=${to}`),
    count: (assocId) => apiRequest(`/v1/audit/count?associationId=${assocId}`),
};

const signatures = {
    getAll: (assocId) => apiRequest(`/v1/signatures?associationId=${assocId}`),
    getActive: (assocId) => apiRequest(`/v1/signatures/active?associationId=${assocId}`),
    upsert: (data) => apiRequest('/v1/signatures', { method: 'POST', body: data }),
    uploadImage: async (sigId, file) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(`/v1/signatures/${sigId}/image`, { method: 'POST', body: formData });
    },
    getImageUrl: async (sigId) => {
        const baseUrl = await getApiUrl();
        return `${baseUrl}/v1/signatures/${sigId}/image`;
    },
    deleteImage: (sigId) => apiRequest(`/v1/signatures/${sigId}/image`, { method: 'DELETE' }),
    delete: (sigId) => apiRequest(`/v1/signatures/${sigId}`, { method: 'DELETE' }),
};

const communications = {
    getTemplates: (assocId) => apiRequest(`/v1/communications/templates?associationId=${assocId}`),
    createTemplate: (data) => apiRequest('/v1/communications/templates', { method: 'POST', body: data }),
    updateTemplate: (id, data) => apiRequest(`/v1/communications/templates/${id}`, { method: 'PUT', body: data }),
    deleteTemplate: (id) => apiRequest(`/v1/communications/templates/${id}`, { method: 'DELETE' }),
    getAll: (assocId) => apiRequest(`/v1/communications?associationId=${assocId}`),
    getById: (id) => apiRequest(`/v1/communications/${id}`),
    create: (data) => apiRequest('/v1/communications', { method: 'POST', body: data }),
    update: (id, data) => apiRequest(`/v1/communications/${id}`, { method: 'PUT', body: data }),
    delete: (id) => apiRequest(`/v1/communications/${id}`, { method: 'DELETE' }),
    resolveRecipients: (id) => apiRequest(`/v1/communications/${id}/resolve-recipients`, { method: 'POST' }),
    getRecipients: (id) => apiRequest(`/v1/communications/${id}/recipients`),
    send: (id, sentBy) => apiRequest(`/v1/communications/${id}/send?sentBy=${requireActorId(sentBy)}`, { method: 'POST' }),
};

const csvImport = {
    getHistory: (assocId) => apiRequest(`/v1/imports?associationId=${assocId}`),
    previewMembers: (file, assocId) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(`/v1/members/import-csv/preview?associationId=${assocId}`, { method: 'POST', body: formData });
    },
    previewActivityParticipants: (file, assocId, activityId) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(`/v1/activities/${activityId}/import-csv?associationId=${assocId}`, { method: 'POST', body: formData });
    },
    previewEventParticipants: (file, assocId, eventId) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(`/v1/events/${eventId}/import-csv?associationId=${assocId}`, { method: 'POST', body: formData });
    },
    importMembers: (file, assocId) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(`/v1/members/import-csv?associationId=${assocId}`, { method: 'POST', body: formData });
    },
    confirmImport: (id, file) => {
        const body = new FormData();
        if (file) body.append('file', file);
        return apiRequest(`/v1/imports/${id}/confirm`, { method: 'POST', body });
    },
};

const fiscal = {
    closeYear: (assocId, year, userId) => apiRequest(`/v1/fiscal/close-year?associationId=${assocId}&year=${year}&userId=${userId}`, { method: 'POST' }),
    getClosure: (assocId, year) => apiRequest(`/v1/fiscal/closure?associationId=${assocId}&year=${year}`),
    runInvoiceCheck: (assocId, year, userId) => apiRequest(`/v1/fiscal/invoice-check?associationId=${assocId}&year=${year}&userId=${userId}`, { method: 'POST' }),
    getInvoiceCheckHistory: (assocId) => apiRequest(`/v1/fiscal/invoice-checks?associationId=${assocId}`),
};

const medicalCertificates = {
    getByMember: (memberId, assocId) => apiRequest(`/v1/medical-certificates?memberId=${memberId}&associationId=${assocId}`),
    getByAssociation: (assocId) => apiRequest(`/v1/medical-certificates/by-association?associationId=${assocId}`),
    getExpiring: (assocId, days = 30) => apiRequest(`/v1/medical-certificates/expiring?associationId=${assocId}&days=${days}`),
    getExpired: (assocId) => apiRequest(`/v1/medical-certificates/expired?associationId=${assocId}`),
    // Solo tipo e scadenza: medico, struttura sanitaria, note libere e il file
    // del certificato sono stati tolti. Erano dati sulla salute (art. 9 GDPR)
    // accanto a nome, codice fiscale e indirizzo, e il file non esisteva
    // davvero — puntava a un disco che al riavvio si azzera.
    save: (data) => apiRequest('/v1/medical-certificates', { method: 'POST', body: data }),
    delete: (id) => apiRequest(`/v1/medical-certificates/${id}`, { method: 'DELETE' }),
};

// Il modulo `federation` (affiliazione CONI / EOC) è stato tolto: serviva a
// un'associazione sportiva dilettantistica. Un'APS non si affilia a una
// federazione, si iscrive al RUNTS — vedi i campi `runts*` su Association.

// ========================================
// EXPORT
// ========================================

export const associago = {
    init: async () => { await initializeApi(); return { baseUrl: API_BASE_URL }; },
    setCurrentAssociation: (id) => { CURRENT_ASSOCIATION_ID = id; },
    getApiUrl,
    getCurrentProfile: () => apiRequest('/v1/me'),
    createBackup: () => apiRequest('/v1/backup/create', { method: 'POST' }),
    checkSession: () => apiRequest('/auth/check'),
    logout: () => apiRequest('/auth/logout', { method: 'POST' }),

    // Auth & Setup (Legacy/Basic)
    login: async (associationId, password) => {
        const result = await apiRequest('/auth/login', { method: 'POST', body: { associationId, password } });
        return { token: result.sessionId, user: { id: "admin", role: "ADMIN" } };
    },

    getRecoveryAssociations: () => apiRequest('/auth/recovery/associations'),

    resetPassword: async (associationId, fiscalCode, newPassword) => {
        const result = await apiRequest('/auth/recovery/reset-password', {
            method: 'POST',
            body: { associationId, fiscalCode, newPassword }
        });
        return result.association;
    },

    setupAssociation: async (data) => {
        const result = await apiRequest('/associations/setup', { method: 'POST', body: data });
        const list = JSON.parse(localStorage.getItem(STORAGE_KEY_ASSOCIATIONS) || '[]');
        list.push({ id: result.id, nome: result.name, email: result.email, tipo: result.type });
        localStorage.setItem(STORAGE_KEY_ASSOCIATIONS, JSON.stringify(list));
        return { id: result.id, nome: result.name, email: result.email, tipo: result.type };
    },

    // Local Storage Helpers
    getKnownAssociations: () => JSON.parse(localStorage.getItem(STORAGE_KEY_ASSOCIATIONS) || '[]'),

    upsertKnownAssociation: (association) => {
        const list = JSON.parse(localStorage.getItem(STORAGE_KEY_ASSOCIATIONS) || '[]');
        const normalized = {
            id: association.id,
            nome: association.nome || association.name,
            email: association.email,
            tipo: association.tipo || association.type
        };
        const index = list.findIndex(a => a.id === normalized.id);
        if (index === -1) {
            list.push(normalized);
        } else {
            list[index] = { ...list[index], ...normalized };
        }
        localStorage.setItem(STORAGE_KEY_ASSOCIATIONS, JSON.stringify(list));
        return normalized;
    },

    updateKnownAssociation: (id, newData) => {
        const list = JSON.parse(localStorage.getItem(STORAGE_KEY_ASSOCIATIONS) || '[]');
        const index = list.findIndex(a => a.id === id);
        if (index !== -1) {
            // Aggiorna solo i campi forniti, mantenendo gli altri (es. id)
            if (newData.nome) list[index].nome = newData.nome;
            if (newData.email) list[index].email = newData.email;
            if (newData.tipo) list[index].tipo = newData.tipo;
            localStorage.setItem(STORAGE_KEY_ASSOCIATIONS, JSON.stringify(list));
        }
    },

    removeKnownAssociation: (id) => {
        const list = JSON.parse(localStorage.getItem(STORAGE_KEY_ASSOCIATIONS) || '[]');
        const newList = list.filter(a => a.id !== id);
        localStorage.setItem(STORAGE_KEY_ASSOCIATIONS, JSON.stringify(newList));
        return newList;
    },
    getPreferences: () => {
        const autologin = localStorage.getItem(STORAGE_KEY_AUTOLOGIN);
        return {
            autologin: autologin ? JSON.parse(autologin) : null,
            language: localStorage.getItem(STORAGE_KEY_LANGUAGE) || 'it',
            theme: localStorage.getItem(STORAGE_KEY_THEME) === 'dark' ? 'dark' : 'light'
        };
    },
    setPreferences: (prefs) => {
        if (Object.prototype.hasOwnProperty.call(prefs, 'autologin')) {
            if (prefs.autologin) {
                localStorage.setItem(STORAGE_KEY_AUTOLOGIN, JSON.stringify(prefs.autologin));
            } else {
                localStorage.removeItem(STORAGE_KEY_AUTOLOGIN);
            }
        }
        if (prefs.language) localStorage.setItem(STORAGE_KEY_LANGUAGE, prefs.language);
        if (prefs.theme === 'light' || prefs.theme === 'dark') localStorage.setItem(STORAGE_KEY_THEME, prefs.theme);
    },
    /**
     * Registra la sorgente del token di accesso (sul web: Clerk).
     * Passare null la rimuove, ad esempio al logout.
     */
    setTokenProvider: (provider) => { TOKEN_PROVIDER = provider; },

    setAuthToken: (token) => {
        // Compatibilità con il flusso desktop, che chiama setAuthToken(null) al
        // logout: senza provider non c'è nulla da azzerare.
        if (token === null) TOKEN_PROVIDER = null;
    },

    validateAssociation: async (id) => {
        try {
            await apiRequest(`/associations/${id}`);
            return true;
        } catch (e) {
            if (e.message && e.message.includes('404')) return false;
            throw e;
        }
    },

    /**
     * Elenco delle associazioni configurate sul backend.
     * Sul web serve al gate Clerk per risolvere l'associazione corrente, che
     * nel desktop veniva scelta nella schermata di login.
     */
    listAssociations: () => apiRequest('/v1/associations'),

    /**
     * Area personale. Nessuno di questi endpoint accetta un identificativo:
     * il soggetto lo decide il token, non il chiamante.
     */
    me: {
        profile: () => apiRequest('/v1/me'),
        shifts: () => apiRequest('/v1/me/presenze'),
        expenses: () => apiRequest('/v1/me/rimborsi')
    },

    getAssociationProfile: (id) => apiRequest(`/associations/${id}`),
    updateAssociationProfile: (id, data) => apiRequest(`/associations/${id}`, { method: 'PUT', body: data }),

    // Locations
    getLocations: (assocId) => apiRequest(`/v1/associations/${assocId}/locations`),
    createLocation: (assocId, data) => apiRequest(`/v1/associations/${assocId}/locations`, { method: 'POST', body: data }),
    updateLocation: (assocId, locId, data) => apiRequest(`/v1/associations/${assocId}/locations/${locId}`, { method: 'PUT', body: data }),
    deleteLocation: (assocId, locId) => apiRequest(`/v1/associations/${assocId}/locations/${locId}`, { method: 'DELETE' }),

    // Documents
    getDocuments: (assocId) => apiRequest(`/v1/associations/${assocId}/documents`),
    uploadDocument: (assocId, file, docType, title) => {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('documentType', docType);
        if (title) formData.append('title', title);
        return apiRequest(`/v1/associations/${assocId}/documents`, { method: 'POST', body: formData });
    },
    deleteDocument: (assocId, docId) => apiRequest(`/v1/associations/${assocId}/documents/${docId}`, { method: 'DELETE' }),

    // Deadlines
    getDeadlines: (assocId) => apiRequest(`/v1/associations/${assocId}/deadlines`),
    getPendingDeadlines: (assocId) => apiRequest(`/v1/associations/${assocId}/deadlines/pending`),
    createDeadline: (assocId, data) => apiRequest(`/v1/associations/${assocId}/deadlines`, { method: 'POST', body: data }),
    updateDeadline: (assocId, dlId, data) => apiRequest(`/v1/associations/${assocId}/deadlines/${dlId}`, { method: 'PUT', body: data }),
    completeDeadline: (assocId, dlId) => apiRequest(`/v1/associations/${assocId}/deadlines/${dlId}/complete`, { method: 'POST' }),
    deleteDeadline: (assocId, dlId) => apiRequest(`/v1/associations/${assocId}/deadlines/${dlId}`, { method: 'DELETE' }),
    uploadLogo: (id, file) => {
        const formData = new FormData();
        formData.append('file', file);
        return apiRequest(`/associations/${id}/logo`, { method: 'POST', body: formData });
    },
    getLogoUrl: async (id) => {
        const baseUrl = await getApiUrl();
        return `${baseUrl}/associations/${id}/logo`;
    },

    // Domains
    attendance: {
        getParticipants: (activityId) => apiRequest(`/v1/attendance/activity/${activityId}/participants`),
        getSessions: (activityId) => apiRequest(`/v1/attendance/activity/${activityId}`),
        getSession: (id) => apiRequest(`/v1/attendance/session/${id}`),
        createSession: (data) => apiRequest('/v1/attendance/session', { method: 'POST', body: data }),
        updateSession: (id, data) => apiRequest(`/v1/attendance/session/${id}`, { method: 'PUT', body: data }),
        deleteSession: (id) => apiRequest(`/v1/attendance/session/${id}`, { method: 'DELETE' }),
        getRecords: (sessionId) => apiRequest(`/v1/attendance/session/${sessionId}/records`),
        createRecord: (data) => apiRequest('/v1/attendance/record', { method: 'POST', body: data }),
        updateRecord: (id, data) => apiRequest(`/v1/attendance/record/${id}`, { method: 'PUT', body: data }),
        deleteRecord: (id) => apiRequest(`/v1/attendance/record/${id}`, { method: 'DELETE' }),
    },
    activities,
    events,
    assemblies,
    members,
    users,
    memberships,
    reports,
    dashboard,
    finance,
    projects,
    suiteProjects,
    inventory,
    volunteers,
    coupons,
    paymentMethods,
    stats,
    notifications,
    budgets,
    balances,
    certificates,
    resources,
    cashRegisters,
    audit,
    signatures,
    communications,
    csvImport,
    fiscal,
    medicalCertificates,
    expenseDocuments,
    transactionAdditions,
    volunteerInsurance,
    attachments,

    // App metadata
    getBackendInfo: () => apiRequest('/v1/app/info'),
};

export default associago;
