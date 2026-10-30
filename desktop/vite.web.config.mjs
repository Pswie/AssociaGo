import { resolve } from 'path'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Build web della SPA del gestionale, separata dal target Electron.
 *
 * `electron.vite.config.mjs` resta la configurazione del desktop e non va
 * toccata: qui usiamo Vite puro, con `src/renderer` come root, perché sul web
 * non esistono i processi main e preload.
 *
 * `base` deve combaciare con il percorso sotto cui la SPA viene servita
 * (il rewrite `/gestionale/*` del sito): da lì `import.meta.env.BASE_URL`
 * alimenta anche il basename del BrowserRouter in `main.jsx`.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  for (const key of ['VITE_API_BASE_URL', 'VITE_CLERK_PRIMARY_URL', 'VITE_CLERK_PUBLISHABLE_KEY']) {
    if (process.env[key] !== undefined) env[key] = process.env[key]
  }
  const origin = (value) => {
    if (!value) return ''
    const url = new URL(value)
    const localDevelopment = mode !== 'production' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    if (!(url.protocol === 'https:' || localDevelopment && url.protocol === 'http:') || url.username || url.password) throw new Error('Origine pubblica della build web non valida')
    return url.origin
  }
  const encodedClerkHost = (env.VITE_CLERK_PUBLISHABLE_KEY || '').replace(/^pk_(test|live)_/, '')
  const clerkHost = encodedClerkHost ? Buffer.from(encodedClerkHost, 'base64').toString('utf8').replace(/\$$/, '') : ''
  const clerkOrigins = clerkHost ? `${origin(`https://${clerkHost}`)} https://*.clerk.com` : 'https://*.clerk.accounts.dev https://*.clerk.com'
  return {
  root: resolve('src/renderer'),
  // Default '/': il gestionale sta su un sottodominio dedicato, che evita di
  // far transitare ogni asset dal proxy del sito. Impostare VITE_BASE_PATH a
  // '/gestionale/' solo se si sceglie il rewrite sullo stesso dominio.
  base: process.env.VITE_BASE_PATH || '/',
  publicDir: resolve('src/renderer/public'),
  resolve: {
    alias: {
      '@renderer': resolve('src/renderer/src')
    }
  },
  plugins: [react(), {
    name: 'associago-web-csp',
    transformIndexHtml(html) {
      return html.replaceAll('%API_ORIGIN%', origin(env.VITE_API_BASE_URL))
        .replaceAll('%PRIMARY_ORIGIN%', origin(env.VITE_CLERK_PRIMARY_URL))
        .replaceAll('%CLERK_HOSTS%', clerkOrigins)
    }
  }],
  build: {
    outDir: resolve('dist-web'),
    emptyOutDir: true,
    rollupOptions: {
      // Niente splash.html: è la finestra di avvio di Electron mentre parte il
      // backend locale. Sul web il backend è già acceso altrove.
      //
      // index.web.html e non index.html: monta `main.web.jsx`, che carica il
      // tema di Angeli del Futuro. Tenerli separati è ciò che impedisce al tema
      // di finire anche nell'applicazione Electron, dove gli import CSS statici
      // di `main.jsx` valgono per entrambi i target.
      input: resolve('src/renderer/index.web.html'),
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('@clerk')) return 'vendor-clerk'
          if (
            id.includes('/react/')
            || id.includes('/react-dom/')
            || id.includes('react-router')
            || id.includes('/scheduler/')
          ) return 'vendor-react'
          if (id.includes('i18next')) return 'vendor-i18n'
          if (id.includes('recharts') || id.includes('/d3-')) return 'vendor-charts'
          return undefined
        }
      }
    }
  },
  server: {
    port: 5173
  }
  }
})
