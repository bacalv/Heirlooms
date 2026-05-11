export const API_URL = import.meta.env.VITE_API_URL ?? ''

let cachedSettings = null
export async function fetchSettings(apiKey) {
  if (cachedSettings) return cachedSettings
  const r = await apiFetch('/api/settings', apiKey)
  if (!r.ok) return { previewDurationSeconds: 15 }
  cachedSettings = await r.json()
  return cachedSettings
}

export async function apiFetch(path, apiKey, options = {}) {
  const headers = { 'X-Api-Key': apiKey }
  if (options.body) headers['Content-Type'] = 'application/json'
  Object.assign(headers, options.headers)
  return fetch(`${API_URL}${path}`, { ...options, headers })
}

// ---- Auth API ---------------------------------------------------------------

export async function authChallenge(username) {
  const r = await fetch(`${API_URL}/api/auth/challenge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json() // { auth_salt: base64url }
}

export async function authLogin(username, authKeyB64url) {
  const r = await fetch(`${API_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, auth_key: authKeyB64url }),
  })
  return r // caller checks status
}

export async function authLogout(sessionToken) {
  await fetch(`${API_URL}/api/auth/logout`, {
    method: 'POST',
    headers: { 'X-Api-Key': sessionToken },
  })
}

export async function authRegister(body) {
  const r = await fetch(`${API_URL}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return r // caller checks status
}

export async function getInvite(sessionToken) {
  const r = await apiFetch('/api/auth/invites', sessionToken)
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json() // { token, expires_at }
}

export async function pairingQr(code) {
  const r = await fetch(`${API_URL}/api/auth/pairing/qr`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
  })
  return r // caller checks status
}

export async function pairingStatus(sessionId) {
  const r = await fetch(`${API_URL}/api/auth/pairing/status?session_id=${encodeURIComponent(sessionId)}`)
  return r // caller checks status
}

// ---- Keys API ---------------------------------------------------------------

export async function putPassphrase(apiKey, { wrappedMasterKeyB64, wrapFormat, argon2Params, saltB64 }) {
  const r = await apiFetch('/api/keys/passphrase', apiKey, {
    method: 'PUT',
    body: JSON.stringify({
      wrappedMasterKey: wrappedMasterKeyB64,
      wrapFormat,
      argon2Params,
      salt: saltB64,
    }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
}

export async function registerDevice(apiKey, { deviceId, deviceLabel, deviceKind, pubkeyFormat, pubkeyB64, wrappedMasterKeyB64, wrapFormat }) {
  const r = await apiFetch('/api/keys/devices', apiKey, {
    method: 'POST',
    body: JSON.stringify({
      deviceId,
      deviceLabel,
      deviceKind,
      pubkeyFormat,
      pubkey: pubkeyB64,
      wrappedMasterKey: wrappedMasterKeyB64,
      wrapFormat,
    }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
}

// ---- Encrypted upload -------------------------------------------------------

export async function initiateEncryptedUpload(apiKey, mimeType) {
  const r = await apiFetch('/api/content/uploads/initiate', apiKey, {
    method: 'POST',
    body: JSON.stringify({ mimeType, storage_class: 'encrypted' }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

export async function initiateResumableUpload(apiKey, storageKey, totalBytes, contentType) {
  const r = await apiFetch('/api/content/uploads/resumable', apiKey, {
    method: 'POST',
    body: JSON.stringify({ storageKey, totalBytes, contentType }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

// PUT raw bytes to a signed URL — no auth header, no Content-Type.
export async function putBlob(signedUrl, bytes) {
  const r = await fetch(signedUrl, { method: 'PUT', body: bytes })
  if (!r.ok) throw new Error(`Blob PUT failed: ${r.status}`)
}

// Same as putBlob but fires onProgress(bytesLoaded) as the upload proceeds.
export function putBlobWithProgress(signedUrl, bytes, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', signedUrl)
    xhr.upload.onprogress = (e) => onProgress(e.loaded)
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(`Blob PUT failed: ${xhr.status}`))
    }
    xhr.onerror = () => reject(new Error('Blob PUT network error'))
    xhr.send(bytes)
  })
}

export async function confirmEncryptedUpload(apiKey, {
  storageKey, mimeType, fileSize,
  envelopeVersion, wrappedDekB64, dekFormat,
  thumbnailStorageKey, wrappedThumbnailDekB64, thumbnailDekFormat,
  encryptedMetadataB64, encryptedMetadataFormat,
  previewStorageKey, wrappedPreviewDekB64, previewDekFormat,
  plainChunkSize, durationSeconds,
  takenAt, tags,
}) {
  const r = await apiFetch('/api/content/uploads/confirm', apiKey, {
    method: 'POST',
    body: JSON.stringify({
      storageKey, mimeType, fileSize, storage_class: 'encrypted',
      envelopeVersion, wrappedDek: wrappedDekB64, dekFormat,
      thumbnailStorageKey, wrappedThumbnailDek: wrappedThumbnailDekB64, thumbnailDekFormat,
      encryptedMetadata: encryptedMetadataB64, encryptedMetadataFormat,
      previewStorageKey, wrappedPreviewDek: wrappedPreviewDekB64, previewDekFormat,
      plainChunkSize, durationSeconds,
      takenAt, tags,
    }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
}

// Fetch arbitrary URL with auth header and return raw bytes.
export async function fetchBytes(url, apiKey) {
  const r = await fetch(url, { headers: { 'X-Api-Key': apiKey } })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return new Uint8Array(await r.arrayBuffer())
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
