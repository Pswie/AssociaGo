import { useCallback, useEffect, useRef, useState } from 'react'
import { associago } from '../api'
import {
  localProjectForContext,
  mayViewProjectFinance,
  safeProjectLink,
  SUITE_UUID
} from '../project-context'

const STATUSES = {
  proposto: 'Proposto',
  attivo: 'Attivo',
  da_completare: 'Da completare',
  completato: 'Completato'
}
const SHIFT_STATUSES = { SCHEDULED: 'Programmato', COMPLETED: 'Svolto', MISSED: 'Non svolto' }
const SYNC_STATUSES = {
  synced: 'Allineato',
  error: 'Errore',
  pending: 'In attesa',
  unlinked: 'Da collegare',
  disabled: 'Non configurato'
}
const EMPTY_PROJECT = {
  name: '',
  status: 'proposto',
  budget: '',
  deadline: '',
  referent: '',
  area: '',
  fundingSource: '',
  opportunityUrl: '',
  needs: '',
  notes: ''
}
const EMPTY_SHIFT = {
  volunteerId: '',
  startTime: '',
  endTime: '',
  role: '',
  hoursWorked: '',
  status: 'SCHEDULED',
  description: ''
}
const array = (value) => (Array.isArray(value) ? value : [])
const personKey = (item) => `${item.personType}:${item.personId}`
const money = (value) =>
  value === null || value === undefined
    ? 'Non disponibile'
    : new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' }).format(Number(value))
const dateTime = (value) =>
  !value || Number.isNaN(new Date(value).getTime())
    ? 'Non disponibile'
    : new Date(value).toLocaleString('it-IT')

function ProjectForm({ value, busy, save, cancel }) {
  const [form, setForm] = useState({
    ...EMPTY_PROJECT,
    ...value,
    budget: value?.budget ?? '',
    deadline: value?.deadline ?? ''
  })
  const field = (key) => ({
    value: form[key] ?? '',
    onChange: (event) => setForm((previous) => ({ ...previous, [key]: event.target.value }))
  })
  const submit = (event) => {
    event.preventDefault()
    const payload = Object.fromEntries(
      Object.keys(EMPTY_PROJECT).map((key) => [key, form[key] === '' ? null : form[key]])
    )
    save({ ...payload, name: form.name.trim(), status: form.status })
  }
  return (
    <section className="card mb-3">
      <div className="card-body">
        <h3 className="h5">{value?.id ? 'Modifica progetto locale' : 'Nuovo progetto locale'}</h3>
        <form onSubmit={submit}>
          <div className="row g-3">
            <div className="col-md-8">
              <label className="form-label" htmlFor="project-name">
                Nome del progetto
              </label>
              <input
                id="project-name"
                className="form-control"
                required
                maxLength={200}
                {...field('name')}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label" htmlFor="project-status">
                Stato operativo
              </label>
              <select id="project-status" className="form-select" {...field('status')}>
                {Object.entries(STATUSES).map(([id, label]) => (
                  <option key={id} value={id}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-md-4">
              <label className="form-label" htmlFor="project-budget">
                Budget preventivo (EUR)
              </label>
              <input
                id="project-budget"
                className="form-control"
                type="number"
                min="0"
                step="0.01"
                {...field('budget')}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label" htmlFor="project-deadline">
                Fine prevista del progetto
              </label>
              <input
                id="project-deadline"
                className="form-control"
                type="date"
                {...field('deadline')}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label" htmlFor="project-referent">
                Referente
              </label>
              <input
                id="project-referent"
                className="form-control"
                maxLength={200}
                {...field('referent')}
              />
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor="project-area">
                Area
              </label>
              <input id="project-area" className="form-control" {...field('area')} />
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor="project-funding">
                Fonte di finanziamento
              </label>
              <input id="project-funding" className="form-control" {...field('fundingSource')} />
            </div>
            <div className="col-12">
              <label className="form-label" htmlFor="project-url">
                Collegamento al bando
              </label>
              <input
                id="project-url"
                className="form-control"
                type="url"
                maxLength={1024}
                {...field('opportunityUrl')}
              />
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor="project-needs">
                Bisogni e attività
              </label>
              <textarea id="project-needs" className="form-control" rows={3} {...field('needs')} />
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor="project-notes">
                Note
              </label>
              <textarea id="project-notes" className="form-control" rows={3} {...field('notes')} />
            </div>
          </div>
          <p className="small text-muted mt-3">
            Il budget è una previsione. Salvare il progetto non crea entrate o uscite.
          </p>
          <div className="d-flex gap-2">
            <button className="btn btn-primary" disabled={busy} type="submit">
              Salva progetto
            </button>
            <button
              className="btn btn-outline-secondary"
              type="button"
              disabled={busy}
              onClick={cancel}
            >
              Annulla
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}

function ContextCard({ context, busy, sync }) {
  return (
    <section className="card mb-3">
      <div className="card-body">
        <h3 className="h5">Collegamento tra i siti</h3>
        <p className="fw-semibold mb-1">{context.title}</p>
        <p className="text-muted small">
          ID comune: <span className="text-break">{context.suiteProjectId}</span> · versione{' '}
          {context.version}
        </p>
        <p>{context.description}</p>
        <div className="d-flex flex-wrap gap-2 mb-3">
          {['librepm', 'bandi'].map((system) => {
            const href = safeProjectLink(context.links?.[system])
            return href ? (
              <a
                key={system}
                className="btn btn-outline-primary"
                href={href}
                target="_blank"
                rel="noopener noreferrer"
              >
                Apri in {system === 'librepm' ? 'LibrePM' : 'Bandi'}
              </a>
            ) : (
              <span key={system} className="text-muted">
                {system === 'librepm' ? 'LibrePM' : 'Bandi'}: link non configurato
              </span>
            )
          })}
          <button
            type="button"
            className="btn btn-outline-secondary"
            disabled={busy}
            onClick={sync}
          >
            Sincronizza ora
          </button>
        </div>
        <ul className="list-unstyled mb-0">
          {array(context.sync).map((peer) => (
            <li key={peer.system} className="mb-2">
              <strong>{peer.system === 'bandi' ? 'Bandi' : 'AssociaGo'}</strong>:{' '}
              {SYNC_STATUSES[peer.status] ?? peer.status}
              <span className="small text-muted">
                {' '}
                · ultimo successo: {dateTime(peer.lastSuccessAt)}
              </span>
              {peer.errorMessage ? <p className="text-danger mb-0">{peer.errorMessage}</p> : null}
            </li>
          ))}
        </ul>
        {context.preparation ? (
          <p className="small mt-3 mb-0">
            Candidatura Bandi: {context.preparation.status} · versione {context.preparation.version}{' '}
            · revisione {context.preparation.reviewDecision ?? 'da verificare'}. Stato operativo e
            candidatura restano distinti.
          </p>
        ) : null}
      </div>
    </section>
  )
}

export default function ProjectsDashboard({ associationId, shell }) {
  const requestedSuiteId = shell?.requestedSuiteProjectId ?? null
  const canFinance = mayViewProjectFinance(shell?.currentUser?.role, Boolean(window.api))
  const [projects, setProjects] = useState([])
  const [catalog, setCatalog] = useState([])
  const [selected, setSelected] = useState(null)
  const [context, setContext] = useState(null)
  const [linkedSuiteId, setLinkedSuiteId] = useState(null)
  const [integration, setIntegration] = useState(null)
  const [error, setError] = useState(null)
  const [suiteError, setSuiteError] = useState(null)
  const [workspaceError, setWorkspaceError] = useState(null)
  const [message, setMessage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [workspaceRefresh, setWorkspaceRefresh] = useState(0)
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState(null)
  const [assignments, setAssignments] = useState([])
  const [candidates, setCandidates] = useState([])
  const [candidate, setCandidate] = useState('')
  const [shifts, setShifts] = useState([])
  const [shiftForm, setShiftForm] = useState(null)
  const [balance, setBalance] = useState(null)
  const [transactions, setTransactions] = useState([])
  const [availableTransactions, setAvailableTransactions] = useState(null)
  const [transactionId, setTransactionId] = useState('')
  const [linkLocalId, setLinkLocalId] = useState('')
  const [linkSuiteId, setLinkSuiteId] = useState('')
  const loadSequence = useRef(0)
  const workspaceSequence = useRef(0)

  const load = useCallback(async () => {
    if (!associationId) return
    const sequence = ++loadSequence.current
    const current = () => sequence === loadSequence.current
    setLoading(true)
    setError(null)
    setSuiteError(null)
    const [localResult, statusResult] = await Promise.allSettled([
      associago.projects.getAll(associationId),
      associago.suiteProjects.status()
    ])
    if (!current()) return
    if (localResult.status === 'rejected') {
      setError(localResult.reason.message)
      setSelected(null)
      setProjects([])
      setLoading(false)
      return
    }
    const local = array(localResult.value)
    setProjects(local)
    const enabled = statusResult.status === 'fulfilled' && statusResult.value?.enabled === true
    setIntegration(enabled)
    if (statusResult.status === 'rejected') setSuiteError(statusResult.reason.message)
    if (requestedSuiteId !== null) {
      setSelected(null)
      setContext(null)
    } else {
      setSelected((previous) => local.find((project) => project.id === previous?.id) ?? null)
      setContext(null)
    }
    if (enabled) {
      try {
        const values = await associago.suiteProjects.getAll()
        if (current()) setCatalog(array(values))
      } catch (reason) {
        if (current()) {
          setCatalog([])
          setSuiteError(reason.message)
        }
      }
    } else setCatalog([])
    if (requestedSuiteId !== null) {
      if (!SUITE_UUID.test(requestedSuiteId)) {
        setSuiteError(
          'Il collegamento contiene un identificativo progetto non valido. Nessun altro progetto è stato aperto.'
        )
      } else if (!enabled) {
        setSuiteError(
          'Il progetto richiesto richiede l’integrazione, che non è attiva o disponibile su questo sito.'
        )
      } else {
        try {
          const value = await associago.suiteProjects.getById(requestedSuiteId)
          if (current()) {
            setContext(value)
            const localProject = localProjectForContext(value, local)
            const localInstance = array(value.instances).find(
              (instance) => instance.system === 'associago' && instance.entity === 'project'
            )
            if (localInstance && !localProject)
              setSuiteError(
                'Il progetto comune esiste, ma il suo progetto locale non è disponibile in questa associazione. Verifica il collegamento e i permessi.'
              )
            setSelected(localProject)
          }
        } catch (reason) {
          if (current()) setSuiteError(reason.message + ' Nessun altro progetto è stato aperto.')
        }
      }
    }
    if (current()) {
      setLoading(false)
      setWorkspaceRefresh((value) => value + 1)
    }
  }, [associationId, requestedSuiteId])

  useEffect(() => {
    load()
    return () => {
      loadSequence.current++
    }
  }, [load])

  const loadWorkspace = useCallback(async () => {
    const sequence = ++workspaceSequence.current
    const id = selected?.id
    setAssignments([])
    setCandidates([])
    setShifts([])
    setBalance(null)
    setTransactions([])
    setAvailableTransactions(null)
    setTransactionId('')
    setCandidate('')
    setLinkedSuiteId(null)
    setWorkspaceError(null)
    setShiftForm(null)
    if (!id) return
    const requests = [
      associago.projects.suite(id),
      associago.projects.assignments(id),
      associago.projects.candidates(id),
      associago.projects.shifts(id)
    ]
    if (canFinance)
      requests.push(associago.projects.balance(id), associago.projects.transactions(id))
    const results = await Promise.allSettled(requests)
    if (sequence !== workspaceSequence.current) return
    const setters = [
      (value) => setLinkedSuiteId(value?.suiteProjectId ?? null),
      (value) => setAssignments(array(value)),
      (value) => setCandidates(array(value)),
      (value) => setShifts(array(value)),
      setBalance,
      (value) => setTransactions(array(value))
    ]
    const failures = []
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') setters[index](result.value)
      else failures.push(result.reason.message)
    })
    if (failures.length) setWorkspaceError([...new Set(failures)].join(' '))
    const link = results[0].status === 'fulfilled' ? results[0].value?.suiteProjectId : null
    if (link && requestedSuiteId === null && integration) {
      try {
        const value = await associago.suiteProjects.getById(link)
        if (sequence === workspaceSequence.current) setContext(value)
      } catch (reason) {
        if (sequence === workspaceSequence.current) setSuiteError(reason.message)
      }
    }
  }, [selected?.id, canFinance, integration, requestedSuiteId, workspaceRefresh])

  useEffect(() => {
    loadWorkspace()
    return () => {
      workspaceSequence.current++
    }
  }, [loadWorkspace])

  const run = async (action, text, refresh = true) => {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const value = await action()
      setMessage(text)
      if (refresh) await loadWorkspace()
      return value
    } catch (reason) {
      setError(reason.message || 'Operazione non riuscita.')
      return undefined
    } finally {
      setBusy(false)
    }
  }
  const selectLocal = (id) => {
    shell?.selectSuiteProject?.(null)
    setSelected(projects.find((project) => String(project.id) === String(id)) ?? null)
    setContext(null)
    setSuiteError(null)
    setMessage(null)
    setForm(null)
  }
  const selectSuite = (id) => {
    setForm(null)
    setMessage(null)
    if (id) shell?.selectSuiteProject?.(id)
    else {
      shell?.selectSuiteProject?.(null)
      setContext(null)
      setSelected(null)
    }
  }
  const saveProject = async (payload) => {
    const result = await run(
      () =>
        form?.id
          ? associago.projects.update(form.id, payload)
          : associago.projects.create({ ...payload, associationId }),
      'Progetto locale salvato.',
      false
    )
    if (result) {
      shell?.selectSuiteProject?.(null)
      setProjects((previous) => [...previous.filter((item) => item.id !== result.id), result])
      setSelected(result)
      setForm(null)
      setContext(null)
    }
  }
  const sync = async () => {
    const id = context?.suiteProjectId ?? linkedSuiteId
    if (!id) return
    const result = await run(
      () => associago.suiteProjects.sync(id),
      'Richiesta completata. Controlla gli esiti per ciascun sito.',
      false
    )
    if (result) {
      setContext(result)
      await load()
    }
  }
  const share = async () => {
    const result = await run(
      () => associago.suiteProjects.share(selected.id),
      'Progetto condiviso nel registro comune.',
      false
    )
    if (result) {
      shell?.selectSuiteProject?.(result.suiteProjectId)
      setContext(result)
      await loadWorkspace()
    }
  }
  const link = async (suiteId, localId) => {
    const result = await run(
      () => associago.suiteProjects.link(suiteId, localId),
      'Collegamento registrato. Sincronizza per aggiornare tutti i riepiloghi.',
      false
    )
    if (result) {
      setContext(result)
      shell?.selectSuiteProject?.(suiteId)
      await load()
    }
  }
  const assign = (event) => {
    event.preventDefault()
    const item = candidates.find((value) => personKey(value) === candidate)
    if (item)
      run(
        () =>
          associago.projects.assign(selected.id, {
            personType: item.personType,
            personId: item.personId
          }),
        'Persona assegnata al progetto.'
      )
  }
  const saveShift = async (event) => {
    event.preventDefault()
    const payload = {
      volunteerId: Number(shiftForm.volunteerId),
      startTime: shiftForm.startTime || null,
      endTime: shiftForm.endTime || null,
      role: shiftForm.role || null,
      hoursWorked: shiftForm.hoursWorked === '' ? null : shiftForm.hoursWorked,
      status: shiftForm.status,
      description: shiftForm.description || null,
      projectId: selected.id
    }
    const saved = await run(
      () =>
        shiftForm.id
          ? associago.volunteers.updateShift(shiftForm.id, payload)
          : associago.projects.createShift(selected.id, payload),
      'Turno salvato.'
    )
    if (saved) setShiftForm(null)
  }
  const volunteers = assignments.filter((item) => item.personType === 'VOLUNTEER')
  const unassigned = candidates.filter(
    (item) => !assignments.some((assignment) => personKey(assignment) === personKey(item))
  )
  const completedHours = shifts
    .filter((shift) => shift.status === 'COMPLETED')
    .reduce((sum, shift) => sum + Number(shift.hoursWorked || 0), 0)
  const sharedWithoutLocal =
    context && !array(context.instances).some((item) => item.system === 'associago')

  return (
    <div className="fade-in">
      <header className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div>
          <h2 className="fw-bold mb-1">Progetti</h2>
          <p className="text-muted mb-0">
            Attività, persone, presenze e rendiconto del progetto scelto. I progetti attraversano
            più esercizi.
          </p>
        </div>
        <div className="d-flex gap-2">
          <button
            type="button"
            className="btn btn-outline-secondary"
            disabled={loading || busy}
            onClick={load}
          >
            Aggiorna
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={busy}
            onClick={() => setForm({ ...EMPTY_PROJECT })}
          >
            Nuovo progetto
          </button>
        </div>
      </header>
      {error ? (
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
      ) : null}
      {message ? (
        <div className="alert alert-success" role="status">
          {message}
        </div>
      ) : null}
      <section className="card mb-3">
        <div className="card-body row g-3">
          <div className="col-lg-6">
            <label htmlFor="project-local-choice" className="form-label">
              Progetto locale dell’associazione
            </label>
            <select
              id="project-local-choice"
              className="form-select"
              disabled={loading || busy}
              value={selected?.id ?? ''}
              onChange={(event) => selectLocal(event.target.value)}
            >
              <option value="">Seleziona un progetto</option>
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name} · {STATUSES[project.status] ?? project.status}
                </option>
              ))}
            </select>
          </div>
          <div className="col-lg-6">
            <label htmlFor="project-suite-choice" className="form-label">
              Progetto comune ai tre siti
            </label>
            <select
              id="project-suite-choice"
              className="form-select"
              disabled={integration !== true || loading || busy}
              value={requestedSuiteId ?? context?.suiteProjectId ?? ''}
              onChange={(event) => selectSuite(event.target.value)}
            >
              <option value="">Seleziona un progetto condiviso</option>
              {catalog.map((project) => (
                <option key={project.suiteProjectId} value={project.suiteProjectId}>
                  {project.title}
                </option>
              ))}
            </select>
          </div>
        </div>
      </section>
      {loading ? <p role="status">Caricamento progetti…</p> : null}
      {integration === false && !suiteError ? (
        <div className="alert alert-secondary" role="status">
          L’integrazione tra siti non è attiva. Puoi usare normalmente i progetti locali.
        </div>
      ) : null}
      {suiteError ? (
        <div className="alert alert-warning" role="alert">
          {suiteError}
        </div>
      ) : null}
      {form ? (
        <ProjectForm
          key={form.id ?? 'new'}
          value={form}
          busy={busy}
          save={saveProject}
          cancel={() => setForm(null)}
        />
      ) : null}
      {context ? (
        <ContextCard context={context} busy={busy} sync={sync} />
      ) : linkedSuiteId ? (
        <section className="alert alert-info">
          <p className="mb-0">
            Questo progetto è collegato al registro:{' '}
            <span className="text-break">{linkedSuiteId}</span>. Il contesto remoto sarà disponibile
            quando l’integrazione tornerà raggiungibile.
          </p>
        </section>
      ) : null}
      {sharedWithoutLocal ? (
        <section className="card mb-3">
          <div className="card-body">
            <h3 className="h5">Progetto locale da collegare</h3>
            <p>
              Il progetto comune esiste già. Sincronizza per creare la sua scheda locale oppure
              collega esplicitamente un progetto dell’associazione.
            </p>
            <div className="d-flex flex-wrap gap-2">
              <button type="button" className="btn btn-primary" disabled={busy} onClick={sync}>
                Allinea e crea la scheda locale
              </button>
              <label className="visually-hidden" htmlFor="project-link-local">
                Progetto locale da collegare
              </label>
              <select
                id="project-link-local"
                className="form-select w-auto"
                value={linkLocalId}
                onChange={(event) => setLinkLocalId(event.target.value)}
              >
                <option value="">Scegli progetto locale</option>
                {projects.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="btn btn-outline-primary"
                disabled={busy || !linkLocalId}
                onClick={() => link(context.suiteProjectId, linkLocalId)}
              >
                Collega progetto esistente
              </button>
            </div>
          </div>
        </section>
      ) : null}
      {!loading && projects.length === 0 && requestedSuiteId === null && !form ? (
        <p>Nessun progetto locale. Crea una scheda oppure seleziona un progetto condiviso.</p>
      ) : null}
      {selected ? (
        <>
          <section className="card mb-3">
            <div className="card-body">
              <div className="d-flex justify-content-between flex-wrap gap-2">
                <div>
                  <h3 className="h4 mb-1">{selected.name}</h3>
                  <p className="text-muted">
                    {STATUSES[selected.status] ?? selected.status} · Referente:{' '}
                    {selected.referent || 'da definire'} · Fine prevista:{' '}
                    {selected.deadline || 'da definire'}
                  </p>
                </div>
                <div className="d-flex gap-2 align-self-start">
                  <button
                    type="button"
                    className="btn btn-outline-primary"
                    disabled={busy}
                    onClick={() => setForm(selected)}
                  >
                    Modifica
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline-danger"
                    disabled={busy}
                    onClick={async () => {
                      if (
                        !window.confirm(
                          'Eliminare il progetto locale? I progetti con collegamenti o presenze storiche devono essere conservati.'
                        )
                      )
                        return
                      const result = await run(
                        async () => {
                          await associago.projects.delete(selected.id)
                          return true
                        },
                        'Progetto eliminato.',
                        false
                      )
                      if (result) {
                        setSelected(null)
                        await load()
                      }
                    }}
                  >
                    Elimina
                  </button>
                </div>
              </div>
              <p>
                {selected.needs ||
                  selected.notes ||
                  'Completa la scheda con obiettivi e attività del progetto.'}
              </p>
              <p className="small">
                Budget preventivo: {money(selected.budget)}. Il preventivo non è un movimento
                contabile.
              </p>
              {integration && !linkedSuiteId && !context ? (
                <div className="border-top pt-3">
                  <h4 className="h6">Collega agli altri siti</h4>
                  <div className="d-flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={busy}
                      onClick={share}
                    >
                      Condividi come nuovo progetto
                    </button>
                    <label className="visually-hidden" htmlFor="project-link-suite">
                      Progetto comune già presente
                    </label>
                    <select
                      id="project-link-suite"
                      className="form-select w-auto"
                      value={linkSuiteId}
                      onChange={(event) => setLinkSuiteId(event.target.value)}
                    >
                      <option value="">Oppure scegli progetto già presente</option>
                      {catalog
                        .filter(
                          (item) =>
                            !array(item.instances).some(
                              (instance) => instance.system === 'associago'
                            )
                        )
                        .map((item) => (
                          <option key={item.suiteProjectId} value={item.suiteProjectId}>
                            {item.title}
                          </option>
                        ))}
                    </select>
                    <button
                      type="button"
                      className="btn btn-outline-primary"
                      disabled={busy || !linkSuiteId}
                      onClick={() => link(linkSuiteId, selected.id)}
                    >
                      Collega esistente
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </section>
          {workspaceError ? (
            <div className="alert alert-warning" role="alert">
              {workspaceError}
            </div>
          ) : null}
          <section className="card mb-3">
            <div className="card-body">
              <h3 className="h5">Soci e volontari assegnati</h3>
              <p className="small text-muted">
                Sono disponibili solo iscrizioni attive dell’associazione e volontari con
                collegamento anagrafico verificabile.
              </p>
              <form className="d-flex flex-wrap gap-2 mb-3" onSubmit={assign}>
                <label className="visually-hidden" htmlFor="project-person">
                  Persona da assegnare
                </label>
                <select
                  id="project-person"
                  className="form-select w-auto"
                  value={candidate}
                  onChange={(event) => setCandidate(event.target.value)}
                  required
                >
                  <option value="">Scegli socio o volontario</option>
                  {unassigned.map((item) => (
                    <option key={personKey(item)} value={personKey(item)}>
                      {item.name || `#${item.personId}`} ·{' '}
                      {item.personType === 'VOLUNTEER' ? 'Volontario' : 'Socio'}
                    </option>
                  ))}
                </select>
                <button
                  type="submit"
                  className="btn btn-outline-primary"
                  disabled={busy || !candidate}
                >
                  Assegna al progetto
                </button>
              </form>
              {assignments.length ? (
                <ul className="list-group list-group-flush">
                  {assignments.map((item) => (
                    <li
                      key={item.id}
                      className="list-group-item d-flex justify-content-between flex-wrap gap-2 px-0"
                    >
                      <span>
                        {item.name}{' '}
                        <span className="badge bg-secondary">
                          {item.personType === 'VOLUNTEER' ? 'Volontario' : 'Socio'}
                        </span>
                      </span>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary"
                        disabled={busy}
                        onClick={() =>
                          run(
                            () => associago.projects.removeAssignment(selected.id, item.id),
                            'Assegnazione rimossa.'
                          )
                        }
                      >
                        Rimuovi assegnazione
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p>Nessuna persona assegnata.</p>
              )}
            </div>
          </section>
          <section className="card mb-3">
            <div className="card-body">
              <div className="d-flex justify-content-between flex-wrap gap-2">
                <div>
                  <h3 className="h5">Turni e ore di volontariato</h3>
                  <p>
                    {volunteers.length} volontari assegnati ·{' '}
                    {completedHours.toLocaleString('it-IT', { maximumFractionDigits: 2 })} ore
                    svolte
                  </p>
                </div>
                <button
                  type="button"
                  className="btn btn-outline-primary align-self-start"
                  disabled={busy || volunteers.length === 0}
                  onClick={() => setShiftForm({ ...EMPTY_SHIFT })}
                >
                  Registra turno
                </button>
              </div>
              {!volunteers.length ? (
                <p className="small text-muted">
                  Assegna almeno un volontario per registrare turni del progetto.
                </p>
              ) : null}
              {shiftForm ? (
                <form className="border rounded p-3 mb-3" onSubmit={saveShift}>
                  <h4 className="h6">{shiftForm.id ? 'Aggiorna turno' : 'Nuovo turno'}</h4>
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label htmlFor="project-shift-volunteer" className="form-label">
                        Volontario assegnato
                      </label>
                      <select
                        id="project-shift-volunteer"
                        className="form-select"
                        required
                        disabled={Boolean(shiftForm.id)}
                        value={shiftForm.volunteerId}
                        onChange={(event) =>
                          setShiftForm((previous) => ({
                            ...previous,
                            volunteerId: event.target.value
                          }))
                        }
                      >
                        <option value="">Seleziona volontario</option>
                        {volunteers.map((item) => (
                          <option key={item.personId} value={item.personId}>
                            {item.name}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="col-md-3">
                      <label htmlFor="project-shift-status" className="form-label">
                        Stato
                      </label>
                      <select
                        id="project-shift-status"
                        className="form-select"
                        value={shiftForm.status}
                        onChange={(event) =>
                          setShiftForm((previous) => ({ ...previous, status: event.target.value }))
                        }
                      >
                        {Object.entries(SHIFT_STATUSES).map(([id, label]) => (
                          <option key={id} value={id}>
                            {label}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="col-md-3">
                      <label htmlFor="project-shift-hours" className="form-label">
                        Ore svolte
                      </label>
                      <input
                        id="project-shift-hours"
                        className="form-control"
                        type="number"
                        min="0"
                        step="0.01"
                        required={shiftForm.status === 'COMPLETED'}
                        value={shiftForm.hoursWorked ?? ''}
                        onChange={(event) =>
                          setShiftForm((previous) => ({
                            ...previous,
                            hoursWorked: event.target.value
                          }))
                        }
                      />
                    </div>
                    {['startTime', 'endTime'].map((key, index) => (
                      <div className="col-md-6" key={key}>
                        <label htmlFor={`project-shift-${key}`} className="form-label">
                          {index === 0 ? 'Inizio' : 'Fine'}
                        </label>
                        <input
                          id={`project-shift-${key}`}
                          className="form-control"
                          type="datetime-local"
                          required
                          value={shiftForm[key] || ''}
                          onChange={(event) =>
                            setShiftForm((previous) => ({ ...previous, [key]: event.target.value }))
                          }
                        />
                      </div>
                    ))}
                    <div className="col-md-6">
                      <label htmlFor="project-shift-role" className="form-label">
                        Ruolo
                      </label>
                      <input
                        id="project-shift-role"
                        className="form-control"
                        value={shiftForm.role || ''}
                        onChange={(event) =>
                          setShiftForm((previous) => ({ ...previous, role: event.target.value }))
                        }
                      />
                    </div>
                    <div className="col-md-6">
                      <label htmlFor="project-shift-description" className="form-label">
                        Descrizione
                      </label>
                      <input
                        id="project-shift-description"
                        className="form-control"
                        value={shiftForm.description || ''}
                        onChange={(event) =>
                          setShiftForm((previous) => ({
                            ...previous,
                            description: event.target.value
                          }))
                        }
                      />
                    </div>
                  </div>
                  <div className="d-flex gap-2 mt-3">
                    <button className="btn btn-primary" type="submit" disabled={busy}>
                      Salva turno
                    </button>
                    <button
                      className="btn btn-outline-secondary"
                      type="button"
                      disabled={busy}
                      onClick={() => setShiftForm(null)}
                    >
                      Annulla
                    </button>
                  </div>
                </form>
              ) : null}
              {shifts.length ? (
                <div className="table-responsive">
                  <table className="table align-middle">
                    <thead>
                      <tr>
                        <th>Volontario</th>
                        <th>Inizio</th>
                        <th>Fine</th>
                        <th>Stato</th>
                        <th>Ore</th>
                        <th>Azioni</th>
                      </tr>
                    </thead>
                    <tbody>
                      {shifts.map((item) => (
                        <tr key={item.id}>
                          <td>
                            {volunteers.find((person) => person.personId === item.volunteerId)
                              ?.name || `Volontario #${item.volunteerId}`}
                          </td>
                          <td>{dateTime(item.startTime)}</td>
                          <td>{dateTime(item.endTime)}</td>
                          <td>{SHIFT_STATUSES[item.status] ?? item.status}</td>
                          <td>{item.hoursWorked ?? '—'}</td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-primary"
                              disabled={busy}
                              onClick={() => setShiftForm({ ...EMPTY_SHIFT, ...item })}
                            >
                              Aggiorna
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p>Nessun turno registrato per questo progetto.</p>
              )}
            </div>
          </section>
          {canFinance ? (
            <section className="card mb-3">
              <div className="card-body">
                <h3 className="h5">Rendiconto reale</h3>
                {balance ? (
                  <>
                    <div className="row g-3 mb-3">
                      {[
                        ['Preventivo', balance.budget],
                        ['Entrate', balance.income],
                        ['Uscite', balance.expense],
                        ['Saldo', balance.saldo]
                      ].map(([label, value]) => (
                        <div className="col-sm-6 col-xl-3" key={label}>
                          <div className="border rounded p-3">
                            <div className="small text-muted">{label}</div>
                            <strong>{money(value)}</strong>
                          </div>
                        </div>
                      ))}
                    </div>
                    {balance.overBudget ? (
                      <p className="text-danger">Le uscite superano il budget preventivo.</p>
                    ) : null}
                  </>
                ) : (
                  <p>Rendiconto non disponibile.</p>
                )}
                <p className="small text-muted">
                  I movimenti vengono collegati dalla contabilità esistente; questo comando non crea
                  una seconda registrazione.
                </p>
                <button
                  type="button"
                  className="btn btn-outline-primary mb-3"
                  disabled={busy}
                  onClick={async () => {
                    const values = await run(
                      () => associago.projects.availableTransactions(selected.id),
                      'Movimenti disponibili caricati.',
                      false
                    )
                    if (values) setAvailableTransactions(array(values))
                  }}
                >
                  Collega un movimento esistente
                </button>
                {availableTransactions ? (
                  <div className="d-flex flex-wrap gap-2 mb-3">
                    <label className="visually-hidden" htmlFor="project-transaction">
                      Movimento da collegare
                    </label>
                    <select
                      id="project-transaction"
                      className="form-select w-auto"
                      value={transactionId}
                      onChange={(event) => setTransactionId(event.target.value)}
                    >
                      <option value="">Scegli movimento dell’associazione</option>
                      {availableTransactions
                        .filter((item) => !item.projectId)
                        .map((item) => (
                          <option key={item.id} value={item.id}>
                            #{item.id} · {item.date} ·{' '}
                            {item.description || item.category || 'Movimento'} ·{' '}
                            {money(item.amount)}
                          </option>
                        ))}
                    </select>
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={busy || !transactionId}
                      onClick={() =>
                        run(
                          () => associago.projects.attachTransaction(selected.id, transactionId),
                          'Movimento collegato al progetto.'
                        )
                      }
                    >
                      Collega movimento
                    </button>
                  </div>
                ) : null}
                {transactions.length ? (
                  <div className="table-responsive">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Data</th>
                          <th>Descrizione</th>
                          <th>Tipo</th>
                          <th>Importo</th>
                        </tr>
                      </thead>
                      <tbody>
                        {transactions.map((item) => (
                          <tr key={item.id}>
                            <td>{item.date}</td>
                            <td>{item.description || item.category || `Movimento #${item.id}`}</td>
                            <td>{item.type === 'INCOME' ? 'Entrata' : 'Uscita'}</td>
                            <td>{money(item.amount)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p>Nessun movimento collegato.</p>
                )}
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  )
}
