import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Badge, Button, Card, Form, Spinner, Table } from 'react-bootstrap'
import { ClipboardCheck, Edit, Plus, Trash2 } from 'lucide-react'
import { associago } from '../api'
import AttendanceForm from './AttendanceForm'
import Modal from './Modal'
import RowActions from './common/RowActions'

const STATUS_LABELS = {
  PRESENT: 'Presente',
  ABSENT: 'Assente',
  LATE: 'In ritardo',
  EXCUSED: 'Giustificato'
}
const dateLabel = (value) =>
  value
    ? new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString('it-IT')
    : 'Data non indicata'
const EMPTY_RECORD = { participantKey: '', status: 'PRESENT', checkInTime: '', notes: '' }
const participantKey = (person) => {
  const membership = Number.isSafeInteger(person.membershipId) && person.membershipId > 0
  const member = Number.isSafeInteger(person.memberId) && person.memberId > 0
  if (membership === member) return ''
  return membership ? `membership:${person.membershipId}` : `member:${person.memberId}`
}
const participantLabel = (person) => {
  const status =
    {
      ACTIVE: 'Attivo',
      EXPIRED: 'Scaduto',
      INACTIVE: 'Non attivo',
      PENDING: 'In attesa',
      SUSPENDED: 'Sospeso'
    }[person.status] || person.status
  return [
    person.displayName,
    person.membershipCardNumber
      ? `tessera ${person.membershipCardNumber}`
      : person.participantType === 'LEGACY_MEMBER'
        ? 'anagrafica storica'
        : null,
    status
  ]
    .filter(Boolean)
    .join(' · ')
}

export default function AttendanceList({ associationId, shell }) {
  const anno = shell?.anno ?? new Date().getFullYear()
  const [activities, setActivities] = useState([])
  const [activityId, setActivityId] = useState('')
  const [sessions, setSessions] = useState([])
  const [selectedSession, setSelectedSession] = useState(null)
  const [records, setRecords] = useState([])
  const [participants, setParticipants] = useState([])
  const [participantError, setParticipantError] = useState('')
  const [loadingParticipants, setLoadingParticipants] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadingSessions, setLoadingSessions] = useState(false)
  const [loadingRecords, setLoadingRecords] = useState(false)
  const [busy, setBusy] = useState(false)
  const [sessionForm, setSessionForm] = useState(null)
  const [recordForm, setRecordForm] = useState(EMPTY_RECORD)
  const [editingRecord, setEditingRecord] = useState(null)
  const sessionRequest = useRef(0)
  const recordRequest = useRef(0)
  const participantRequest = useRef(0)
  const participantByKey = useMemo(
    () =>
      new Map(
        participants
          .filter((person) => participantKey(person))
          .map((person) => [participantKey(person), person])
      ),
    [participants]
  )
  const legacyInstructors = useMemo(
    () =>
      participants
        .filter((person) => person.participantType === 'LEGACY_MEMBER' && person.memberId)
        .map((person) => ({ id: person.memberId, displayName: person.displayName })),
    [participants]
  )

  const loadActivities = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await associago.activities.getAll(associationId, anno)
      setActivities(data || [])
      setActivityId((previous) =>
        data?.some((activity) => String(activity.id) === previous)
          ? previous
          : String(data?.[0]?.id || '')
      )
    } catch (err) {
      setError(err.message || 'Attività non disponibili. Riprova.')
    } finally {
      setLoading(false)
    }
  }, [associationId, anno])
  useEffect(() => {
    if (associationId) loadActivities()
  }, [loadActivities, associationId])

  const loadParticipants = useCallback(async () => {
    const request = ++participantRequest.current
    if (!activityId) {
      setParticipants([])
      return
    }
    setLoadingParticipants(true)
    setParticipantError('')
    try {
      const data = await associago.attendance.getParticipants(Number(activityId))
      if (request === participantRequest.current) setParticipants(data || [])
    } catch (err) {
      if (request === participantRequest.current)
        setParticipantError(err.message || 'Partecipanti non disponibili. Riprova.')
    } finally {
      if (request === participantRequest.current) setLoadingParticipants(false)
    }
  }, [activityId])
  useEffect(() => {
    setParticipants([])
    loadParticipants()
    return () => {
      participantRequest.current += 1
    }
  }, [loadParticipants])

  const loadSessions = useCallback(async () => {
    const request = ++sessionRequest.current
    if (!activityId) {
      setSessions([])
      return
    }
    setLoadingSessions(true)
    setError('')
    try {
      const data = await associago.attendance.getSessions(Number(activityId))
      if (request === sessionRequest.current) setSessions(data || [])
    } catch (err) {
      if (request === sessionRequest.current)
        setError(err.message || 'Sessioni non disponibili. Riprova.')
    } finally {
      if (request === sessionRequest.current) setLoadingSessions(false)
    }
  }, [activityId])
  useEffect(() => {
    setSelectedSession(null)
    setRecords([])
    setSessions([])
    loadSessions()
    return () => {
      sessionRequest.current += 1
    }
  }, [loadSessions])

  const loadRecords = useCallback(async () => {
    const request = ++recordRequest.current
    if (!selectedSession?.id) return
    setLoadingRecords(true)
    setError('')
    try {
      const data = await associago.attendance.getRecords(selectedSession.id)
      if (request === recordRequest.current) setRecords(data || [])
    } catch (err) {
      if (request === recordRequest.current)
        setError(err.message || 'Presenze non disponibili. Riprova.')
    } finally {
      if (request === recordRequest.current) setLoadingRecords(false)
    }
  }, [selectedSession?.id])
  useEffect(() => {
    setRecords([])
    setRecordForm(EMPTY_RECORD)
    setEditingRecord(null)
    loadRecords()
    return () => {
      recordRequest.current += 1
    }
  }, [loadRecords])

  const deleteSession = async (session) => {
    if (!confirm('Eliminare questa sessione e le presenze registrate?')) return
    setBusy(true)
    try {
      await associago.attendance.deleteSession(session.id)
      if (selectedSession?.id === session.id) setSelectedSession(null)
      await loadSessions()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const changeStatus = async (record, status) => {
    setBusy(true)
    setError('')
    try {
      const saved = await associago.attendance.updateRecord(record.id, { ...record, status })
      setRecords((previous) => previous.map((item) => (item.id === saved.id ? saved : item)))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const addRecord = async (event) => {
    event.preventDefault()
    const participant = editingRecord || participantByKey.get(recordForm.participantKey)
    if (busy || !participant) return
    setBusy(true)
    setError('')
    try {
      const payload = {
        sessionId: selectedSession.id,
        memberId: participant.memberId ?? null,
        membershipId: participant.membershipId ?? null,
        status: recordForm.status,
        checkInTime: recordForm.checkInTime || null,
        notes: recordForm.notes.trim() || null
      }
      const saved = editingRecord
        ? await associago.attendance.updateRecord(editingRecord.id, payload)
        : await associago.attendance.createRecord(payload)
      setRecords((previous) =>
        editingRecord
          ? previous.map((item) => (item.id === saved.id ? saved : item))
          : [...previous, saved]
      )
      setRecordForm(EMPTY_RECORD)
      setEditingRecord(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const deleteRecord = async (record) => {
    if (!confirm('Rimuovere questa registrazione di presenza?')) return
    setBusy(true)
    setError('')
    try {
      await associago.attendance.deleteRecord(record.id)
      setRecords((previous) => previous.filter((item) => item.id !== record.id))
      if (editingRecord?.id === record.id) {
        setEditingRecord(null)
        setRecordForm(EMPTY_RECORD)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const sessionsForYear = sessions
    .filter((session) => session.date?.startsWith(String(anno)))
    .sort((a, b) => b.date.localeCompare(a.date))
  const availableParticipants = participants.filter(
    (person) =>
      participantKey(person) &&
      !records.some((record) => participantKey(record) === participantKey(person))
  )
  const recordField = (name) => ({
    value: recordForm[name],
    disabled: busy,
    onChange: (event) => {
      const value = event.target.value
      setRecordForm((previous) => ({ ...previous, [name]: value }))
    }
  })

  return (
    <div className="fade-in">
      <div className="d-flex flex-wrap justify-content-between gap-3 align-items-start mb-4">
        <div>
          <h2 className="fw-bold mb-1">Registro presenze</h2>
          <p className="text-muted mb-0">
            Sessioni delle attività e partecipazioni nell’esercizio {anno}.
          </p>
        </div>
        <Button
          onClick={() => setSessionForm({})}
          disabled={!activityId || loading || loadingSessions}
        >
          <Plus size={18} className="me-2" />
          Nuova sessione
        </Button>
      </div>
      {error && (
        <Alert variant="danger" role="alert">
          {error}
          <Button
            variant="link"
            onClick={selectedSession ? loadRecords : activityId ? loadSessions : loadActivities}
          >
            Riprova
          </Button>
        </Alert>
      )}
      <Form.Group controlId="attendance-activity" className="mb-4">
        <Form.Label>Attività</Form.Label>
        <Form.Select
          value={activityId}
          onChange={(event) => setActivityId(event.target.value)}
          disabled={loading || busy}
        >
          <option value="">{loading ? 'Caricamento attività…' : 'Seleziona un’attività'}</option>
          {activities.map((activity) => (
            <option key={activity.id} value={activity.id}>
              {activity.name}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      {loading ? (
        <div role="status" className="text-center p-4">
          <Spinner animation="border" />
          <span className="visually-hidden">Caricamento attività</span>
        </div>
      ) : !activities.length && !error ? (
        <Alert variant="info">
          Non ci sono attività in questo esercizio. Aggiungi prima un’attività dalla relativa
          sezione.
        </Alert>
      ) : null}
      {activityId && (
        <Card>
          <Card.Header>
            <h3 className="h5 mb-0">Sessioni · {anno}</h3>
          </Card.Header>
          <Card.Body className="p-0">
            {loadingSessions ? (
              <p role="status" className="p-4 mb-0">
                Caricamento sessioni…
              </p>
            ) : (
              <Table responsive hover className="mb-0">
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Orario</th>
                    <th>Note</th>
                    <th className="text-end">Azioni</th>
                  </tr>
                </thead>
                <tbody>
                  {sessionsForYear.map((session) => (
                    <tr
                      key={session.id}
                      className={selectedSession?.id === session.id ? 'table-active' : ''}
                    >
                      <td>
                        <Button
                          variant="link"
                          className="p-0"
                          disabled={busy}
                          onClick={() => setSelectedSession(session)}
                        >
                          {dateLabel(session.date)}
                        </Button>
                      </td>
                      <td>
                        {session.startTime?.slice(0, 5) || '—'}
                        {session.endTime ? ` – ${session.endTime.slice(0, 5)}` : ''}
                      </td>
                      <td>{session.notes || '—'}</td>
                      <td>
                        <RowActions
                          actions={[
                            {
                              key: 'records',
                              label: 'Apri presenze',
                              icon: <ClipboardCheck size={16} />,
                              disabled: busy,
                              onClick: () => setSelectedSession(session)
                            },
                            {
                              key: 'edit',
                              label: 'Modifica sessione',
                              icon: <Edit size={16} />,
                              disabled: busy,
                              onClick: () => setSessionForm(session)
                            },
                            {
                              key: 'delete',
                              label: 'Elimina sessione',
                              icon: <Trash2 size={16} />,
                              textClass: 'text-danger',
                              disabled: busy,
                              onClick: () => deleteSession(session)
                            }
                          ]}
                        />
                      </td>
                    </tr>
                  ))}
                  {!sessionsForYear.length && (
                    <tr>
                      <td colSpan={4} className="text-center text-muted py-4">
                        Nessuna sessione per questa attività nell’esercizio {anno}.
                      </td>
                    </tr>
                  )}
                </tbody>
              </Table>
            )}
          </Card.Body>
        </Card>
      )}
      {selectedSession && (
        <Card>
          <Card.Header className="d-flex flex-wrap gap-2 justify-content-between align-items-center">
            <h3 className="h5 mb-0">Presenze del {dateLabel(selectedSession.date)}</h3>
            <Badge bg="secondary">{records.length} registrazioni</Badge>
          </Card.Header>
          <Card.Body>
            {participantError && (
              <Alert variant="warning" role="alert">
                {participantError}{' '}
                <Button variant="link" onClick={loadParticipants}>
                  Riprova partecipanti
                </Button>
              </Alert>
            )}
            {loadingRecords ? (
              <p role="status">Caricamento presenze…</p>
            ) : (
              <>
                <Table responsive className="align-middle">
                  <thead>
                    <tr>
                      <th>Partecipante</th>
                      <th>Presenza</th>
                      <th>Ingresso</th>
                      <th>Note</th>
                      <th className="text-end">Azioni</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.map((record) => {
                      const person = participantByKey.get(participantKey(record))
                      const name =
                        person?.displayName ||
                        (record.membershipId
                          ? `Socio · iscrizione #${record.membershipId}`
                          : `Partecipante storico #${record.memberId}`)
                      return (
                        <tr key={record.id}>
                          <td>{name}</td>
                          <td>
                            <Form.Select
                              value={record.status}
                              disabled={busy}
                              aria-label={`Presenza di ${name}`}
                              onChange={(event) => changeStatus(record, event.target.value)}
                            >
                              {Object.entries(STATUS_LABELS).map(([value, label]) => (
                                <option key={value} value={value}>
                                  {label}
                                </option>
                              ))}
                            </Form.Select>
                          </td>
                          <td>{record.checkInTime?.slice(0, 5) || '—'}</td>
                          <td>{record.notes || '—'}</td>
                          <td>
                            <RowActions
                              actions={[
                                {
                                  key: 'edit-record',
                                  label: 'Modifica presenza',
                                  icon: <Edit size={16} />,
                                  disabled: busy,
                                  onClick: () => {
                                    setEditingRecord(record)
                                    setRecordForm({
                                      participantKey: participantKey(record),
                                      status: record.status,
                                      checkInTime: record.checkInTime?.slice(0, 5) || '',
                                      notes: record.notes || ''
                                    })
                                  }
                                },
                                {
                                  key: 'delete-record',
                                  label: 'Rimuovi presenza',
                                  icon: <Trash2 size={16} />,
                                  textClass: 'text-danger',
                                  disabled: busy,
                                  onClick: () => deleteRecord(record)
                                }
                              ]}
                            />
                          </td>
                        </tr>
                      )
                    })}
                    {!records.length && (
                      <tr>
                        <td colSpan={5} className="text-muted text-center py-3">
                          Nessuna presenza registrata.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </Table>
                {editingRecord || (!loadingParticipants && availableParticipants.length > 0) ? (
                  <form onSubmit={addRecord} className="border-top pt-3">
                    <h4 className="h6 mb-3">
                      {editingRecord ? 'Modifica presenza' : 'Aggiungi partecipante'}
                    </h4>
                    <div className="row g-3">
                      <div className="col-md-6">
                        <label className="form-label" htmlFor="attendance-member">
                          Partecipante all’attività
                        </label>
                        <select
                          id="attendance-member"
                          className="form-select"
                          required
                          {...recordField('participantKey')}
                          disabled={busy || Boolean(editingRecord)}
                        >
                          <option value="">Seleziona un partecipante</option>
                          {editingRecord && (
                            <option value={participantKey(editingRecord)}>
                              {participantByKey.get(participantKey(editingRecord))?.displayName ||
                                (editingRecord.membershipId
                                  ? `Socio · iscrizione #${editingRecord.membershipId}`
                                  : `Partecipante storico #${editingRecord.memberId}`)}
                            </option>
                          )}
                          {availableParticipants.map((person) => (
                            <option key={participantKey(person)} value={participantKey(person)}>
                              {participantLabel(person)}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div className="col-md-3">
                        <label className="form-label" htmlFor="attendance-status">
                          Presenza
                        </label>
                        <select
                          id="attendance-status"
                          className="form-select"
                          {...recordField('status')}
                        >
                          {Object.entries(STATUS_LABELS).map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div className="col-md-3">
                        <label className="form-label" htmlFor="attendance-checkin">
                          Ora di ingresso
                        </label>
                        <input
                          id="attendance-checkin"
                          type="time"
                          className="form-control"
                          {...recordField('checkInTime')}
                        />
                      </div>
                      <div className="col-12">
                        <label className="form-label" htmlFor="attendance-record-note">
                          Note
                        </label>
                        <input
                          id="attendance-record-note"
                          className="form-control"
                          maxLength={255}
                          {...recordField('notes')}
                        />
                      </div>
                    </div>
                    <div className="d-flex justify-content-end gap-2 mt-3">
                      {editingRecord && (
                        <Button
                          variant="secondary"
                          disabled={busy}
                          onClick={() => {
                            setEditingRecord(null)
                            setRecordForm(EMPTY_RECORD)
                          }}
                        >
                          Annulla modifica
                        </Button>
                      )}
                      <Button type="submit" disabled={busy || !recordForm.participantKey}>
                        {busy
                          ? 'Salvataggio…'
                          : editingRecord
                            ? 'Salva presenza'
                            : 'Registra presenza'}
                      </Button>
                    </div>
                  </form>
                ) : loadingParticipants ? (
                  <p role="status">Caricamento partecipanti…</p>
                ) : participants.length > 0 ? (
                  <Alert variant="info" className="mb-0">
                    Tutti i partecipanti disponibili hanno già una presenza registrata per questa
                    sessione.
                  </Alert>
                ) : (
                  !participantError && (
                    <Alert variant="info" className="mb-0">
                      Non ci sono partecipanti disponibili per questa attività. Aggiungi i soci
                      dall’anagrafica dell’associazione prima di registrarne la presenza.
                    </Alert>
                  )
                )}
              </>
            )}
          </Card.Body>
        </Card>
      )}
      <Modal
        isOpen={Boolean(sessionForm)}
        onClose={() => setSessionForm(null)}
        title={sessionForm?.id ? 'Modifica sessione' : 'Nuova sessione'}
      >
        {sessionForm && (
          <AttendanceForm
            activityId={activityId}
            session={sessionForm}
            members={legacyInstructors}
            onCancel={() => setSessionForm(null)}
            onSuccess={(saved) => {
              setSessionForm(null)
              loadSessions()
              setSelectedSession(saved)
            }}
          />
        )}
      </Modal>
    </div>
  )
}
