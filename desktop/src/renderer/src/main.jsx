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
import { clerkReturnUrl, clerkSatelliteOptions } from './clerk-sso'

// Nel desktop la pagina è caricata da file://, dove i path veri non esistono:
// serve HashRouter. Sul web serve BrowserRouter, con basename perche' la SPA
// e' servita sotto /gestionale e non alla radice del dominio.
const isDesktop = typeof window !== 'undefined' && Boolean(window.api)
const Router = isDesktop ? HashRouter : BrowserRouter
const routerProps = isDesktop
  ? {}
  : { basename: import.meta.env.BASE_URL?.replace(/\/$/, '') || '/' }

const clerkKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

// Development Clerk sessions need the SDK's primary/satellite handshake even
// across these subdomains. Navigation and transfer remain inside Clerk.
const clerkPrimaryUrl = import.meta.env.VITE_CLERK_PRIMARY_URL
let clerkSatelliteProps = {}
let clerkConfigurationError = false
try {
  if (!isDesktop) clerkSatelliteProps = clerkSatelliteOptions(clerkPrimaryUrl, window.location)
} catch {
  clerkConfigurationError = true
}
const returnUrl = isDesktop ? '/' : clerkReturnUrl(window.location)

if (!isDesktop && (!clerkKey || clerkConfigurationError)) {
  // Meglio una pagina che dice cosa manca di una schermata bianca: senza chiave
  // Clerk non esiste alcun modo di autenticarsi, e il backend rifiuta tutto.
  document.getElementById('root').innerHTML =
    '<p style="font-family:system-ui;padding:2rem;color:#a34a24">' +
    'Accesso temporaneamente non disponibile. Riprova tra poco o contatta chi gestisce il servizio.' +
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
              signInFallbackRedirectUrl={returnUrl}
              signUpFallbackRedirectUrl={returnUrl}
              {...clerkSatelliteProps}
            >
              <WebAuthGate isSatellite={clerkSatelliteProps.isSatellite === true}>
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
