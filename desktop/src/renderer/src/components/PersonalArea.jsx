import { useEffect, useRef, useState } from 'react'
import { Alert, Badge, Card, Spinner, Table } from 'react-bootstrap'
import { associago } from '../api'
import { ORG_NAME, ORG_LOGO } from '../branding'
import ResponsiveTables from './ResponsiveTables'

/**
 * Area personale di soci e volontari.
 *
 * Il soggetto è sempre quello del token Clerk: nessun id personale arriva
 * dall'interfaccia. L'aspetto resta quello originale React Bootstrap
 * dell'area amministrativa.
 *
 * È l'unica schermata che vive fuori dal guscio dell'applicazione — niente
 * binario laterale, niente barra in alto — e insieme quella con più
 * probabilità di essere aperta da un telefono: un socio la consulta per la
 * scadenza della tessera, un volontario per le ore registrate, e nessuno dei
 * due apre il portatile per farlo. Per questo monta per conto proprio
 * `ResponsiveTables` e porta la classe `jl-page`, che in `assets/mobile.css`
 * è l'aggancio delle regole sui margini e sulle testate.
 */
export default function PersonalArea() {
  const [profile, setProfile] = useState(null)
  const [shifts, setShifts] = useState([])
  const [expenses, setExpenses] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const rootRef = useRef(null)

  useEffect(() => {
    let cancelled = false

    Promise.all([
      associago.me.profile(),
      associago.me.shifts(),
      associago.me.expenses()
    ])
      .then(([profileData, shiftData, expenseData]) => {
        if (cancelled) return
        setProfile(profileData)
        setShifts(shiftData ?? [])
        setExpenses(expenseData ?? [])
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  if (loading) {
    return (
      <div className="d-flex align-items-center gap-2 text-muted p-4">
        <Spinner animation="border" size="sm" />
        <span>Caricamento della tua posizione…</span>
      </div>
    )
  }

  if (error) {
    return (
      <Alert variant="danger" className="m-4">
        <Alert.Heading>Non riesco a leggere la tua posizione</Alert.Heading>
        <p className="mb-0">{error}</p>
      </Alert>
    )
  }

  const totalHours = shifts.reduce(
    (sum, shift) => sum + (Number(shift.ore) || 0),
    0
  )

  return (
    <div className="jl-page p-4" ref={rootRef}>
      <ResponsiveTables rootRef={rootRef} />

      <header className="mb-4">
        <img src={ORG_LOGO} alt={ORG_NAME} width="80" height="80" className="mb-3" style={{ objectFit: 'contain', background: '#fff', padding: 6, borderRadius: 12 }} />
        <h1 className="h3">La tua posizione</h1>
        <p className="text-muted">{ORG_NAME}</p>
      </header>

      <Card className="mb-4">
        <Card.Header>
          <h2 className="h5 mb-1">
            {profile?.nome} {profile?.cognome}
          </h2>
          <span className="text-muted">{profile?.email}</span>
        </Card.Header>
        <Card.Body>
          {(profile?.posizioni ?? []).length === 0 ? (
            <p className="text-muted mb-0">
              Nessuna posizione associativa registrata. Se pensi sia un errore,
              parlane con il referente.
            </p>
          ) : (
            <ul className="list-unstyled mb-0">
              {profile.posizioni.map((position, index) => (
                <li
                  key={index}
                  className="d-flex flex-wrap align-items-center gap-2 mb-2"
                >
                  <Badge bg={position.stato === 'ACTIVE' ? 'success' : 'warning'}>
                    {position.stato}
                  </Badge>
                  <span>{position.ruolo}</span>
                  {position.tessera && (
                    <span className="text-muted">tessera {position.tessera}</span>
                  )}
                  {position.scadenza && (
                    <span className="text-muted">scade il {position.scadenza}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card.Body>
      </Card>

      <Card className="mb-4">
        <Card.Header>
          <h2 className="h5 mb-1">Le tue ore</h2>
          <span className="text-muted">
            {totalHours > 0
              ? `${totalHours.toFixed(1)} ore registrate`
              : 'Nessuna ora registrata finora'}
          </span>
        </Card.Header>
        {shifts.length > 0 && (
          <Card.Body>
            <Table responsive hover className="mb-0" aria-label="Le tue ore di volontariato">
              <thead>
                <tr>
                  <th>Inizio</th>
                  <th>Ore</th>
                  <th>Ruolo</th>
                  <th>Stato</th>
                </tr>
              </thead>
              <tbody>
                {shifts.map((shift, index) => (
                  <tr key={index}>
                    <td>{formatDateTime(shift.inizio)}</td>
                    <td>{shift.ore || '—'}</td>
                    <td>{shift.ruolo || '—'}</td>
                    <td>{shift.stato || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card.Body>
        )}
      </Card>

      <Card>
        <Card.Header>
          <h2 className="h5 mb-1">I tuoi rimborsi</h2>
          <span className="text-muted">
            {expenses.length > 0
              ? `${expenses.length} richieste`
              : 'Nessun rimborso richiesto'}
          </span>
        </Card.Header>
        {expenses.length > 0 && (
          <Card.Body>
            <Table responsive hover className="mb-0" aria-label="I tuoi rimborsi">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Importo</th>
                  <th>Descrizione</th>
                  <th>Stato</th>
                </tr>
              </thead>
              <tbody>
                {expenses.map((expense, index) => (
                  <tr key={index}>
                    <td>{expense.data || '—'}</td>
                    <td>{formatEuro(expense.importo)}</td>
                    <td>{expense.descrizione || '—'}</td>
                    <td>
                      <Badge bg={expense.stato === 'PAID' ? 'success' : 'secondary'}>
                        {expense.stato || '—'}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card.Body>
        )}
      </Card>
    </div>
  )
}

function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatEuro(value) {
  if (value === null || value === undefined || value === '') return '—'
  return new Intl.NumberFormat('it-IT', {
    style: 'currency',
    currency: 'EUR'
  }).format(Number(value))
}
