import { useEffect, useState } from 'react'
import { RedirectToSignIn, SignIn, useAuth, useUser } from '@clerk/clerk-react'
import { associago } from '../api'
import { ORG_NAME, SOURCE_CODE_URL } from '../branding'
import { clerkApplicationRole } from '../roles'
import { initialWebAssociation } from '../association-choice'
import { clerkSignInReturnOptions } from '../clerk-sso'
import AppIcon from '../assets/6.svg'

/**
 * Accesso al gestionale sul web.
 *
 * <p>Sostituisce {@code LoginPage}, che resta la strada del desktop: quella
 * schermata usa una master password per associazione, e sul web l'endpoint che
 * la valida è chiuso di proposito ({@code CloudSecurityConfig}).
 *
 * <p>Due compiti soli:
 * <ol>
 *   <li>registrare in {@code api.js} la sorgente del token Clerk, così ogni
 *       richiesta parte con l'header Authorization;
 *   <li>risolvere l'associazione corrente, che nel desktop veniva scelta al
 *       login: si usa l'ID di installazione esplicito o l'unica associazione.
 *       Se ne esistono diverse, la persona deve scegliere senza un ripiego implicito.
 * </ol>
 *
 * @param {boolean} [props.isSatellite] Vero quando questa applicazione è un
 *   satellite di Clerk, cioè quando la sessione è tenuta dal sito e non da qui
 *   (configurazione in {@code main.jsx}). In quel caso chi non è autenticato
 *   non deve vedere un secondo modulo di accesso: verrebbe chiesta una seconda
 *   volta la stessa credenziale, che è il difetto che i satelliti eliminano.
 *   Si va invece al primario, che riconosce la sessione già aperta e rimanda
 *   indietro senza mostrare nulla.
 */
export default function WebAuthGate({ children, isSatellite = false }) {
  const { isLoaded, isSignedIn, getToken, signOut, sessionClaims } = useAuth()
  const { user } = useUser()

  const [association, setAssociation] = useState(null)
  const [localUserId, setLocalUserId] = useState(null)
  const [error, setError] = useState(null)
  const [retryAttempt, setRetryAttempt] = useState(0)
  const [availableAssociations, setAvailableAssociations] = useState([])
  const [associationChoice, setAssociationChoice] = useState('')

  // Il provider va registrato prima di qualunque chiamata: getToken() rinnova
  // da solo il token quando sta per scadere.
  useEffect(() => {
    if (!isSignedIn) {
      associago.setTokenProvider(null)
      return
    }
    associago.setTokenProvider(() => getToken())
  }, [isSignedIn, getToken])

  useEffect(() => {
    if (!isSignedIn) {
      setAssociation(null)
      setLocalUserId(null)
      setError(null)
      setAvailableAssociations([])
      setAssociationChoice('')
      associago.setCurrentAssociation(null)
      return
    }

    let cancelled = false
    setError(null)
    setAssociation(null)
    setLocalUserId(null)
    setAvailableAssociations([])
    setAssociationChoice('')
    associago.setCurrentAssociation(null)
    Promise.all([
      associago.listAssociations(),
      associago.getCurrentProfile().catch((err) => {
        if (err.status === 404) return null
        throw err
      })
    ])
      .then(([list, profile]) => {
        if (cancelled) return
        const current = initialWebAssociation(list, import.meta.env.VITE_ASSOCIATION_ID)
        setAvailableAssociations(list)
        setLocalUserId(Number.isSafeInteger(profile?.id) && profile.id > 0 ? profile.id : null)
        if (!current) return
        associago.setCurrentAssociation(current.id)
        setAssociation(current)
      })
      .catch((err) => {
        if (!cancelled) setError(err)
      })

    return () => {
      cancelled = true
    }
  }, [isSignedIn, user?.id, retryAttempt])

  if (!isLoaded) {
    return <CenteredMessage>Verifica dell’accesso…</CenteredMessage>
  }

  if (!isSignedIn) {
    // Da satellite l'accesso è del primario. RedirectToSignIn e non un
    // window.location scritto a mano: costruisce l'indirizzo passando dallo
    // scambio di Clerk, che è ciò che trasferisce la sessione e riporta qui.
    // Un redirect artigianale arriverebbe alla schermata di accesso del sito
    // senza portarsi dietro il punto di ritorno.
    if (isSatellite) {
      return (
        <CenteredMessage>
          Riconoscimento dell’accesso…
          <RedirectToSignIn {...clerkSignInReturnOptions(window.location)} />
        </CenteredMessage>
      )
    }

    return (
      <div
        className="min-vh-100 d-flex flex-column align-items-center justify-content-center p-4"
        style={{ background: '#fdfbf7' }}
      >
        <img src={AppIcon} alt={ORG_NAME} width="56" height="56" className="mb-3" />
        <h1 className="h5 mb-1" style={{ color: '#16281e' }}>
          {ORG_NAME}
        </h1>
        <p className="text-muted small mb-4">Gestionale dell&apos;associazione</p>
        <SignIn routing="hash" fallbackRedirectUrl={clerkSignInReturnOptions(window.location).redirectUrl} />
        <a
          href={SOURCE_CODE_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="text-muted small text-decoration-none mt-4"
        >
          Codice sorgente · AGPLv3
        </a>
      </div>
    )
  }

  if (error) {
    const status = error.status
    const details = typeof error === 'string' ? error : error.message || ''
    const description =
      status === 401
        ? 'La sessione è scaduta. Esci e accedi di nuovo.'
        : status === 403
          ? 'Il tuo account non ha accesso a questa associazione. Contatta chi gestisce gli accessi.'
          : status >= 500 || /HTTP 50\d|Failed to fetch|NetworkError|Load failed/i.test(details)
            ? 'Il servizio non è al momento disponibile. Riprova tra poco.'
            : 'Non è stato possibile completare l’accesso. Riprova o contatta chi gestisce il servizio.'
    return (
      <CenteredMessage tone="danger">
        <div role="alert">
          <h1 className="h5 mb-3">Impossibile aprire il gestionale</h1>
          <p className="mb-3">{description}</p>
        </div>
        <div className="d-flex gap-2 flex-wrap justify-content-center">
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setRetryAttempt((attempt) => attempt + 1)}
          >
            Riprova
          </button>
          <button
            type="button"
            className="btn btn-outline-secondary"
            onClick={() => signOut().catch((err) => setError(err))}
          >
            Esci
          </button>
        </div>
      </CenteredMessage>
    )
  }

  if (!association) {
    if (availableAssociations.length > 1) {
      return (
        <CenteredMessage>
          <form
            onSubmit={(event) => {
              event.preventDefault()
              try {
                const current = initialWebAssociation(availableAssociations, associationChoice)
                if (!current) return
                associago.setCurrentAssociation(current.id)
                setAssociation(current)
              } catch (reason) {
                setError(reason)
              }
            }}
          >
            <h1 className="h5 mb-3">Scegli l’associazione</h1>
            <p>
              Seleziona l’associazione in cui vuoi lavorare.
            </p>
            <label className="form-label" htmlFor="web-association-choice">
              Associazione di lavoro
            </label>
            <select
              id="web-association-choice"
              className="form-select mb-3"
              required
              value={associationChoice}
              onChange={(event) => setAssociationChoice(event.target.value)}
            >
              <option value="">Seleziona associazione</option>
              {availableAssociations.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name || item.nome || `Associazione #${item.id}`}
                </option>
              ))}
            </select>
            <div className="d-flex justify-content-center gap-2">
              <button type="submit" className="btn btn-primary" disabled={!associationChoice}>
                Apri associazione
              </button>
              <button
                type="button"
                className="btn btn-outline-secondary"
                onClick={() => signOut().catch((reason) => setError(reason))}
              >
                Esci
              </button>
            </div>
          </form>
        </CenteredMessage>
      )
    }
    return <CenteredMessage>Caricamento dell&apos;associazione…</CenteredMessage>
  }

  // signOut viaggia come callback invece che via hook: App gira anche nel
  // desktop, fuori da ClerkProvider, dove useClerk() solleverebbe un errore.
  return children({
    association,
    localUserId,
    user,
    role: clerkApplicationRole(user, sessionClaims),
    signOut
  })
}

function CenteredMessage({ children, tone }) {
  return (
    <div
      className="min-vh-100 d-flex align-items-center justify-content-center p-4"
      style={{ background: '#fdfbf7' }}
    >
      <div
        className={`${tone === 'danger' ? 'text-danger' : 'text-muted'} text-center`}
        style={{ maxWidth: 520 }}
      >
        {children}
      </div>
    </div>
  )
}
