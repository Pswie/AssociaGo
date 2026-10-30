/** Build return destinations from this browser origin, never from redirect input. */
export function clerkReturnUrl(location) {
  const url = new URL(location.origin)
  url.pathname = location.pathname || '/'
  url.search = location.search || ''
  url.hash = location.hash || ''
  for (const name of [...url.searchParams.keys()]) {
    if (name.startsWith('__clerk') || /^(redirect_url|after_sign_(in|up)_url|sign_(in|up)_(force|fallback)_redirect_url)$/i.test(name)) {
      url.searchParams.delete(name)
    }
  }
  return url.href
}

export function clerkSignInReturnOptions(location) {
  const destination = clerkReturnUrl(location)
  return {
    redirectUrl: destination,
    signInForceRedirectUrl: destination,
    signUpForceRedirectUrl: destination,
    signInFallbackRedirectUrl: destination,
    signUpFallbackRedirectUrl: destination
  }
}

export function clerkSatelliteOptions(primary, location) {
  if (!primary?.trim()) return {}
  const url = new URL(primary.trim())
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
  if (url.username || url.password || url.search || url.hash || !['', '/'].includes(url.pathname) || !(url.protocol === 'https:' || url.protocol === 'http:' && loopback)) {
    throw new Error('Invalid primary origin')
  }
  return {
    isSatellite: true,
    satelliteAutoSync: true,
    domain: location.host,
    signInUrl: `${url.origin}/admin`,
    signUpUrl: `${url.origin}/admin`
  }
}
