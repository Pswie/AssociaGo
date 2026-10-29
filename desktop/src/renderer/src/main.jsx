import 'bootstrap/dist/css/bootstrap.min.css';
import './assets/main.css'
// Dopo main.css perché ne corregge le misure sotto una certa larghezza: nei
// moduli ES l'ordine degli import è quello di valutazione, e Rollup emette il
// CSS nello stesso ordine. Vale per entrambi i target, desktop e web — a
// differenza del tema, qui non c'è nulla di estetico da tenere separato, solo
// il comportamento del layout su finestra stretta.
import './assets/mobile.css'
import './i18n'; // Import i18n configuration

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, HashRouter } from 'react-router-dom'
import { ClerkProvider } from '@clerk/clerk-react'
import App from './App'
import UserActionTracker from './components/UserActionTracker'
import WebAuthGate from './components/WebAuthGate'

// Nel desktop la pagina è caricata da file://, dove i path veri non esistono:
// serve HashRouter. Sul web serve BrowserRouter, con basename perche' la SPA
// e' servita sotto /gestionale e non alla radice del dominio.
const isDesktop = typeof window !== 'undefined' && Boolean(window.api)
const Router = isDesktop ? HashRouter : BrowserRouter
const routerProps = isDesktop
  ? {}
  : { basename: import.meta.env.BASE_URL?.replace(/\/$/, '') || '/' }

const clerkKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

/**
 * Configurazione da applicazione satellite di Clerk.
 *
 * Il problema che risolve: le tre applicazioni stanno su tre domini registrabili
 * diversi (`terzo-settore.vercel.app`, `adf-bandi-collaudo.vercel.app`,
 * `adf-gestionale-collaudo.vercel.app`). La sessione di Clerk vive in un cookie,
 * e `vercel.app` è nella Public Suffix List: il browser **rifiuta** di impostare
 * un cookie valido per `.vercel.app`. Condividere la stessa istanza Clerk non
 * basta quindi a condividere la sessione — chi entrava da `/admin` doveva
 * riautenticarsi qui, che è esattamente ciò che l'architettura a "una sola
 * identità" prometteva di evitare.
 *
 * Le satellite domains sono il meccanismo che Clerk ha progettato per questo
 * caso: un dominio primario tiene la sessione, i satelliti la leggono con uno
 * scambio via redirect, senza cookie di terze parti — che i browser bloccano.
 *
 * Vale solo per la build web con `VITE_CLERK_PRIMARY_URL` impostata. Senza
 * quella variabile l'applicazione resta autonoma con la sua schermata di
 * accesso: è così che continua a funzionare lo sviluppo locale, dove un
 * satellite puntato a un primario inesistente non riuscirebbe ad autenticare
 * nessuno. Il desktop non passa da qui.
 *
 * Da fare fuori dal codice, una volta sola: nel pannello Clerk i due domini
 * satellite vanno dichiarati accanto al primario, altrimenti lo scambio viene
 * rifiutato. In modalità sviluppo la funzione è gratuita.
 */
const clerkPrimaryUrl = import.meta.env.VITE_CLERK_PRIMARY_URL?.replace(/\/$/, '')
const clerkSatelliteProps = clerkPrimaryUrl
  ? {
      isSatellite: true,
      // Il proprio dominio, non quello del primario: la chiave pubblica è la
      // stessa per tutte e tre le applicazioni, e questo è il modo in cui
      // Clerk distingue chi sta chiedendo.
      domain: window.location.host,
      // La schermata di accesso è quella del sito: `/admin` monta <SignIn />.
      signInUrl: `${clerkPrimaryUrl}/admin`,
      signUpUrl: `${clerkPrimaryUrl}/admin`
    }
  : {}

if (!isDesktop && !clerkKey) {
  // Meglio una pagina che dice cosa manca di una schermata bianca: senza chiave
  // Clerk non esiste alcun modo di autenticarsi, e il backend rifiuta tutto.
  document.getElementById('root').innerHTML =
    '<p style="font-family:system-ui;padding:2rem;color:#a34a24">' +
    'VITE_CLERK_PUBLISHABLE_KEY non configurata: la build web non ha un modo per autenticare.' +
    '</p>'
} else {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <Router {...routerProps} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <UserActionTracker>
          {isDesktop ? (
            <App />
          ) : (
            <ClerkProvider
              publishableKey={clerkKey}
              afterSignOutUrl={import.meta.env.BASE_URL || '/'}
              {...clerkSatelliteProps}
            >
              <WebAuthGate isSatellite={Boolean(clerkPrimaryUrl)}>
                {({ association, localUserId, user, role, signOut }) => (
                  <App webSession={{ association, localUserId, user, role, signOut }} />
                )}
              </WebAuthGate>
            </ClerkProvider>
          )}
        </UserActionTracker>
      </Router>
    </StrictMode>
  )
}
