import { useState } from 'react'

function Versions() {
  // Sul web `window.electron` non esiste: senza guardia il componente
  // esploderebbe al primo render nel browser.
  const [versions] = useState(() => window.electron?.process?.versions ?? null)

  if (!versions) return null

  return (
    <ul className="versions">
      <li className="electron-version">Electron v{versions.electron}</li>
      <li className="chrome-version">Chromium v{versions.chrome}</li>
      <li className="node-version">Node v{versions.node}</li>
    </ul>
  )
}

export default Versions
