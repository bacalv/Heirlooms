const MEM_MAX = 300

let _masterKey = null

export const thumbnailCache = new Map()

export function unlock(masterKey) {
  _masterKey = masterKey
}

export function lock() {
  _masterKey = null
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
