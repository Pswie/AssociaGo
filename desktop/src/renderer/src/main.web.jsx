/**
 * Entry point della build web del gestionale.
 *
 * Esiste per una ragione sola: caricare il tema di Angeli del Futuro **senza**
 * applicarlo anche all'applicazione Electron. Gli import CSS sono statici, quindi
 * un import dentro `main.jsx` — che è condiviso fra desktop e web — finirebbe in
 * entrambi i bundle e cambierebbe l'aspetto del desktop, che non lo ha chiesto.
 *
 * L'ordine degli import conta: `main.jsx` porta con sé Bootstrap e `main.css`,
 * e il tema deve venire dopo per poterli sovrascrivere. In un modulo ES gli
 * import vengono valutati nell'ordine in cui sono scritti, e Rollup emette il
 * CSS nello stesso ordine.
 *
 * @author Angeli del Futuro
 * @since 0.2.0
 */

// Il font sta prima di tutto: è una dipendenza del tema, non un suo override.
import '@fontsource-variable/plus-jakarta-sans'

// Avvia l'applicazione (Bootstrap, main.css, i18n, React root).
import './main.jsx'

// Ultimo: vince su quanto sopra.
import './assets/adf-theme.css'
