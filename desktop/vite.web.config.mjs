import { resolve } from 'path'
import { defineConfig } from 'vite'
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
export default defineConfig({
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
  plugins: [react()],
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
})
