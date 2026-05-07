export const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function apiFetch(path, apiKey, options = {}) {
  const headers = { 'X-Api-Key': apiKey }
  if (options.body) headers['Content-Type'] = 'application/json'
  Object.assign(headers, options.headers)
  return fetch(`${API_URL}${path}`, { ...options, headers })
}

export function formatUnlockDate(isoString) {
  return new Date(isoString).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

export function formatUploadDate(isoString) {
  return new Date(isoString).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

export function joinRecipients(recipients) {
  if (!recipients || recipients.length === 0) return ''
  if (recipients.length === 1) return recipients[0]
  if (recipients.length === 2) return `${recipients[0]} and ${recipients[1]}`
  const last = recipients[recipients.length - 1]
  const rest = recipients.slice(0, -1).join(', ')
  return `${rest}, and ${last}`
}

export function capsuleTitle(recipients) {
  if (!recipients || recipients.length === 0) return 'A capsule'
  return `For ${joinRecipients(recipients)}`
}

export function formatCompactDate(isoString) {
  const d = new Date(isoString)
  const opts = { day: 'numeric', month: 'short' }
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = 'numeric'
  return d.toLocaleDateString('en-GB', opts)
}

export function daysUntilPurge(compostedAtIso) {
  const purgeAt = new Date(new Date(compostedAtIso).getTime() + 90 * 24 * 60 * 60 * 1000)
  return Math.max(0, Math.ceil((purgeAt - Date.now()) / (24 * 60 * 60 * 1000)))
}

export function buildUnlockAt(day, month, year) {
  const monthNum = String(month).padStart(2, '0')
  const dayNum = String(day).padStart(2, '0')
  const dt = new Date(`${year}-${monthNum}-${dayNum}T08:00:00`)
  const tzOffset = -dt.getTimezoneOffset()
  const sign = tzOffset >= 0 ? '+' : '-'
  const absOffset = Math.abs(tzOffset)
  const oh = String(Math.floor(absOffset / 60)).padStart(2, '0')
  const om = String(absOffset % 60).padStart(2, '0')
  return `${year}-${monthNum}-${dayNum}T08:00:00${sign}${oh}:${om}`
}
