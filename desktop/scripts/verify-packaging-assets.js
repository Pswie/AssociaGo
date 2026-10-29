const fs = require('fs')
const path = require('path')

const desktopDir = path.resolve(__dirname, '..')

const requiredFiles = [
  'out/main/index.js',
  'out/preload/index.js',
  'out/renderer/index.html',
  'out/renderer/splash.html'
]

const missing = requiredFiles.filter((relativePath) => {
  return !fs.existsSync(path.join(desktopDir, relativePath))
})

// Local require() calls must refer to emitted files, not source-only helpers.
const mainBundle = path.join(desktopDir, 'out/main/index.js')
if (fs.existsSync(mainBundle)) {
  const source = fs.readFileSync(mainBundle, 'utf8')
  for (const match of source.matchAll(/require\(["'](\.[^"']+)["']\)/g)) {
    const modulePath = path.resolve(path.dirname(mainBundle), match[1])
    if (!['', '.js', '.cjs', '.json'].some(extension => fs.existsSync(modulePath + extension))) {
      missing.push(`out/main dependency ${match[1]}`)
    }
  }
}

const iconPairs = [
  ['build/icon.png', 'resources/icon.png'],
  ['build/icon.ico', 'resources/icon.ico'],
  ['build/icon.icns', 'resources/icon.icns'],
  ['build/background.png', 'resources/background.png']
]

for (const [preferredPath, fallbackPath] of iconPairs) {
  const preferredExists = fs.existsSync(path.join(desktopDir, preferredPath))
  const fallbackExists = fs.existsSync(path.join(desktopDir, fallbackPath))

  if (!preferredExists && !fallbackExists) {
    missing.push(`${preferredPath} (or ${fallbackPath})`)
  }
}

if (missing.length > 0) {
  console.error('[verify-packaging-assets] Missing required files:')
  for (const relativePath of missing) {
    console.error(`  - ${relativePath}`)
  }
  process.exit(1)
}

console.log('[verify-packaging-assets] Packaging assets look consistent.')
