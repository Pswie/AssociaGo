/** Resolve an explicit installation ID, or the only association. Never guess among many. */
export function initialWebAssociation(list, configuredId = '') {
  if (!Array.isArray(list) || list.length === 0) {
    throw new Error(
      'Nessuna associazione configurata sul backend. Contatta chi gestisce l’installazione.'
    )
  }
  const value = String(configuredId ?? '').trim()
  if (value) {
    if (!/^[1-9][0-9]*$/.test(value) || !Number.isSafeInteger(Number(value))) {
      throw new Error(
        'VITE_ASSOCIATION_ID non valido: configura un identificativo locale numerico positivo.'
      )
    }
    const association = list.find((item) => String(item.id) === value)
    if (!association) {
      throw new Error(
        'L’associazione configurata per questo sito non è presente sul backend. Nessun’altra associazione è stata selezionata.'
      )
    }
    return association
  }
  return list.length === 1 ? list[0] : null
}
