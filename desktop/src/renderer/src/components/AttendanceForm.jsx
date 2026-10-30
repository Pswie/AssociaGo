import { useState } from 'react'
import { associago } from '../api'

const localToday = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

export default function AttendanceForm({ activityId, session, members = [], onSuccess, onCancel }) {
  const [form, setForm] = useState({
    date: session?.date || localToday(),
    startTime: session?.startTime?.slice(0, 5) || '',
    endTime: session?.endTime?.slice(0, 5) || '',
    instructorId: session?.instructorId || '',
    notes: session?.notes || ''
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const field = (name) => ({
    id: `attendance-session-${name}`,
    value: form[name],
    onChange: (event) => {
      const value = event.target.value
      setForm((previous) => ({ ...previous, [name]: value }))
    }
  })
  const submit = async (event) => {
    event.preventDefault()
    if (saving) return
    setError('')
    if (form.startTime && form.endTime && form.endTime < form.startTime) {
      setError('L’orario di fine non può precedere l’inizio.')
      return
    }
    setSaving(true)
    try {
      const payload = {
        activityId: Number(activityId),
        date: form.date,
        startTime: form.startTime || null,
        endTime: form.endTime || null,
        instructorId: form.instructorId ? Number(form.instructorId) : null,
        notes: form.notes.trim() || null
      }
      const saved = session?.id
        ? await associago.attendance.updateSession(session.id, payload)
        : await associago.attendance.createSession(payload)
      onSuccess(saved)
    } catch (err) {
      setError(err.message || 'Sessione non salvata. Riprova.')
    } finally {
      setSaving(false)
    }
  }
  return (
    <form onSubmit={submit}>
      {error && (
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
      )}
      <fieldset disabled={saving}>
        <div className="row g-3">
          <div className="col-12">
            <label className="form-label" htmlFor="attendance-session-date">
              Data della sessione
            </label>
            <input type="date" className="form-control" required {...field('date')} />
          </div>
          <div className="col-sm-6">
            <label className="form-label" htmlFor="attendance-session-startTime">
              Ora di inizio
            </label>
            <input type="time" className="form-control" {...field('startTime')} />
          </div>
          <div className="col-sm-6">
            <label className="form-label" htmlFor="attendance-session-endTime">
              Ora di fine
            </label>
            <input type="time" className="form-control" {...field('endTime')} />
          </div>
          {(members.length > 0 || form.instructorId) && (
            <div className="col-12">
              <label className="form-label" htmlFor="attendance-session-instructorId">
                Istruttore (facoltativo)
              </label>
              <select className="form-select" {...field('instructorId')}>
                <option value="">Nessun istruttore</option>
                {form.instructorId &&
                  !members.some((member) => String(member.id) === String(form.instructorId)) && (
                    <option value={form.instructorId}>
                      Istruttore storico #{form.instructorId}
                    </option>
                  )}
                {members.map((member) => (
                  <option key={member.id} value={member.id}>
                    {member.displayName || `${member.firstName || ''} ${member.lastName || ''}`} · #
                    {member.id}
                  </option>
                ))}
              </select>
            </div>
          )}
          <div className="col-12">
            <label className="form-label" htmlFor="attendance-session-notes">
              Note della sessione
            </label>
            <textarea className="form-control" rows={3} maxLength={255} {...field('notes')} />
          </div>
        </div>
      </fieldset>
      <div className="d-flex flex-wrap justify-content-end gap-2 mt-4">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={saving}>
          Annulla
        </button>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Salvataggio…' : 'Salva sessione'}
        </button>
      </div>
    </form>
  )
}
