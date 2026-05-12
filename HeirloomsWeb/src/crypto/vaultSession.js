const MEM_MAX = 300

let _masterKey = null
let _sharingPrivkey = null
const _plotKeys = new Map() // plotId → raw 32-byte Uint8Array

export const thumbnailCache = new Map()

export function unlock(masterKey) {
  _masterKey = masterKey
}

export function setSharingPrivkey(cryptoKey) {
  _sharingPrivkey = cryptoKey
}

export function getSharingPrivkey() {
  return _sharingPrivkey
}

export function setPlotKey(plotId, rawKeyBytes) {
  _plotKeys.set(plotId, rawKeyBytes)
}

export function getPlotKey(plotId) {
  return _plotKeys.get(plotId) ?? null
}

export function lock() {
  _masterKey = null
  _sharingPrivkey = null
  _plotKeys.clear()
  thumbnailCache.forEach(url => URL.revokeObjectURL(url))
  thumbnailCache.clear()
}

export function isUnlocked() {
  return _masterKey !== null
}

export function getMasterKey() {
  if (!_masterKey) throw new Error('Vault is locked')
  return _masterKey
}

export function cacheThumbnail(uploadId, objectUrl) {
  if (thumbnailCache.size >= MEM_MAX) {
    const oldest = thumbnailCache.keys().next().value
    URL.revokeObjectURL(thumbnailCache.get(oldest))
    thumbnailCache.delete(oldest)
  }
  thumbnailCache.set(uploadId, objectUrl)
}
