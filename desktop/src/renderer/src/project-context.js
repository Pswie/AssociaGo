export const SUITE_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function safeProjectLink(value) {
  if (typeof value !== 'string') return null
  try {
    const url = new URL(value)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password
      ? url.href
      : null
  } catch {
    return null
  }
}

export function localProjectForContext(context, projects) {
  const instance = context?.instances?.find(
    (item) => item.system === 'associago' && item.entity === 'project'
  )
  if (!instance) return null
  return projects.find((item) => String(item.id) === String(instance.localId)) ?? null
}

export function mayViewProjectFinance(role, desktop = false) {
  if (desktop) return true
  const normalized = String(role || '')
    .trim()
    .toLowerCase()
    .replaceAll('_', '-')
    .replaceAll(' ', '-')
  return ['presidente', 'responsabile-amministrativo'].includes(normalized)
}
