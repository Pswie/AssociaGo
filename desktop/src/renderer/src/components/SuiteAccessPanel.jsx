import { useCallback, useEffect, useState } from 'react';
import { associago } from '../api';
import { changeModulePermission, editableAccess } from '../suite-access.js';

const ROLE_LABELS = { none: 'Nessun accesso', viewer: 'Lettore', editor: 'Operatore', manager: 'Responsabile', custom: 'Personalizzato' };
const STATUS_LABELS = { pending: 'In attesa', active: 'Attivo', suspended: 'Sospeso' };

/** Same authoritative access list as Gestionale and Bandi; no local role copy. */
export default function SuiteAccessPanel({ user, api = associago }) {
  const [catalog, setCatalog] = useState(null);
  const [page, setPage] = useState({ items: [], total: 0, hasMore: false });
  const [query, setQuery] = useState('');
  const [offset, setOffset] = useState(0);
  const [selected, setSelected] = useState(null);
  const [draft, setDraft] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [syncOffset, setSyncOffset] = useState(0);
  const [reload, setReload] = useState(0);
  const canManage = user?.access?.canManageAccess === true && user?.access?.owner === true;
  const choose = useCallback((row) => { setSelected(row); setDraft(editableAccess(row)); setConflict(false); setError(''); setMessage(''); }, []);

  useEffect(() => {
    if (!canManage) return;
    const controller = new AbortController();
    api.accessCatalog({ signal: controller.signal }).then(setCatalog).catch(problem => { if (!controller.signal.aborted) setError(problem.message); });
    return () => controller.abort();
  }, [canManage, api]);

  useEffect(() => {
    if (!canManage) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setBusy(true);
      api.accessUsers({ offset, limit: 20, q: query }, { signal: controller.signal })
        .then(value => { if (!controller.signal.aborted) setPage(value); })
        .catch(problem => { if (!controller.signal.aborted) setError(problem.message); })
        .finally(() => { if (!controller.signal.aborted) setBusy(false); });
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [canManage, query, offset, reload, api]);

  if (!canManage) return null;

  async function save(event) {
    event.preventDefault();
    if (!selected || selected.owner || busy || conflict) return;
    setBusy(true); setError(''); setMessage('');
    try {
      const saved = await api.accessUpdate(selected.subject, draft);
      choose(saved); setMessage('Accessi aggiornati per tutti e tre i siti.'); setReload(value => value + 1);
    } catch (problem) {
      if (problem.status === 409) { setConflict(true); setError('Questi accessi sono stati modificati altrove. Ricarica l’utente prima di salvare; le modifiche non sono state sovrascritte.'); }
      else setError(problem.message || 'Impossibile salvare gli accessi.');
    } finally { setBusy(false); }
  }

  async function refreshSelected() {
    setBusy(true);
    try {
      const result = await api.accessUsers({ q: selected.subject, offset: 0, limit: 20 });
      const row = result.items.find(item => item.subject === selected.subject);
      if (!row) throw new Error('Utente non trovato. Ricarica l’elenco.');
      choose(row);
    } catch (problem) { setError(problem.message); }
    finally { setBusy(false); }
  }

  async function sync() {
    setBusy(true); setError(''); setMessage('');
    try {
      const result = await api.accessSync(syncOffset);
      setSyncOffset(result.complete ? 0 : result.nextOffset);
      setMessage(result.complete ? `Elenco Clerk aggiornato: ${result.synced} iscritti verificati.` : 'Prima parte aggiornata. Premi Continua sincronizzazione per gli altri iscritti.');
      setReload(value => value + 1);
    } catch (problem) { setError(problem.message || 'Impossibile sincronizzare gli iscritti.'); }
    finally { setBusy(false); }
  }

  function setRole(app, role) {
    setDraft(previous => {
      const old = previous.applications[app];
      const current = old.role === 'custom' ? old.permissions : catalog.presets?.[app]?.[old.role] ?? [];
      return { ...previous, applications: { ...previous.applications, [app]: { role, permissions: role === 'custom' ? [...current] : [] } } };
    });
  }

  return <section className="card mb-4" aria-labelledby="suite-access-title">
    <div className="card-header"><h5 className="mb-0" id="suite-access-title">Utenti e accessi</h5></div>
    <div className="card-body">
      <p>Approva gli iscritti e scegli cosa possono consultare o modificare in Gestionale, Progetti e Bandi.</p>
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      {message && <p className="alert alert-success" role="status">{message}</p>}
      <div className="d-flex gap-2 flex-wrap align-items-end mb-3">
        <label className="flex-grow-1">Cerca utente<input className="form-control" value={query} onChange={event => { setQuery(event.target.value); setOffset(0); }} autoComplete="off" /></label>
        <button type="button" className="btn btn-outline-primary" disabled={busy || !catalog?.directoryConfigured} onClick={sync}>{syncOffset ? 'Continua sincronizzazione' : 'Aggiorna iscritti da Clerk'}</button>
      </div>
      {catalog && !catalog.directoryConfigured && <p className="text-muted">La sincronizzazione dell’elenco Clerk non è disponibile. Gli utenti che accedono ai siti compaiono comunque qui.</p>}
      <div className="list-group mb-3" aria-label="Utenti registrati" aria-busy={busy}>
        {page.items.map(row => <button key={row.subject} type="button" className={`list-group-item list-group-item-action text-start ${selected?.subject === row.subject ? 'active' : ''}`} onClick={() => choose(row)}>
          <span className="d-block fw-semibold">{row.displayName || row.subject}{row.owner ? ' · Proprietario' : ''}</span>
          <span className="small">{row.email || row.subject} · {STATUS_LABELS[row.status]}</span>
        </button>)}
        {!page.items.length && <p className="p-2 mb-0">{busy ? 'Caricamento utenti…' : 'Nessun utente trovato.'}</p>}
      </div>
      <div className="d-flex align-items-center justify-content-between mb-4">
        <button type="button" className="btn btn-outline-secondary btn-sm" disabled={busy || offset === 0} onClick={() => setOffset(value => Math.max(0, value - 20))}>Precedenti</button>
        <span className="small">{page.total} utenti</span>
        <button type="button" className="btn btn-outline-secondary btn-sm" disabled={busy || !page.hasMore} onClick={() => setOffset(value => value + 20)}>Successivi</button>
      </div>
      {selected && draft && catalog && <form onSubmit={save}>
        <h6>Accessi di {selected.displayName || selected.subject}</h6>
        {selected.owner && <p className="alert alert-info">L’account proprietario mantiene il controllo completo e non può essere modificato.</p>}
        <fieldset disabled={busy || selected.owner || conflict}>
          <label className="d-block mb-3">Stato<select className="form-select" value={draft.status} onChange={event => setDraft(previous => ({ ...previous, status: event.target.value }))}>
            {Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          {Object.entries(catalog.applications).map(([app, definition]) => {
            const assignment = draft.applications[app];
            const shownPermissions = assignment.role === 'custom' ? assignment.permissions : catalog.presets?.[app]?.[assignment.role] ?? [];
            return <fieldset className="border rounded p-3 mb-3" key={app}>
              <legend className="float-none w-auto fs-6 px-1">{definition.label}</legend>
              <label className="d-block mb-3">Ruolo in {definition.label}<select className="form-select" value={assignment.role} onChange={event => setRole(app, event.target.value)}>
                {catalog.roles.map(role => <option key={role} value={role}>{ROLE_LABELS[role] || role}</option>)}
              </select></label>
              <div className="table-responsive"><table className="table table-sm align-middle mb-0"><thead><tr><th scope="col">Area</th><th scope="col">Lettura</th><th scope="col">Modifica</th></tr></thead><tbody>
                {Object.entries(definition.modules).map(([module, label]) => <tr key={module}><th scope="row">{label}</th>{['read', 'write'].map(action => {
                  const permission = `${app}.${module}.${action}`;
                  return <td key={action}><input type="checkbox" className="form-check-input" aria-label={`${action === 'read' ? 'Leggere' : 'Modificare'} ${label} in ${definition.label}`} checked={shownPermissions.includes(permission)} onChange={event => setDraft(previous => ({ ...previous, applications: { ...previous.applications, [app]: changeModulePermission({ ...assignment, permissions: shownPermissions }, permission, event.target.checked) } }))} /></td>;
                })}</tr>)}
              </tbody></table></div>
            </fieldset>;
          })}
          <p className="small text-muted">La modifica include sempre la lettura. I ruoli operativi non concedono la gestione degli accessi né le operazioni AI riservate al proprietario.</p>
          {!selected.owner && <button type="submit" className="btn btn-primary">{busy ? 'Salvataggio…' : 'Salva accessi'}</button>}
        </fieldset>
        {conflict && <button type="button" className="btn btn-outline-primary" disabled={busy} onClick={refreshSelected}>Ricarica questo utente</button>}
      </form>}
    </div>
  </section>;
}
