// Thumbnail cache: in-memory LRU (session) + Cache API (cross-session).
// Each call returns a fresh blob URL that the caller owns and must revoke.

const MEM_MAX = 300
const DISK_MAX = 500
const CACHE_NAME = 'heirlooms-thumbs-v1'
// Must be a valid URL string for the Cache API key.
const CACHE_KEY_BASE = 'https://heirlooms-cache/thumb/'

// In-memory LRU: Map insertion order = LRU order (oldest first, newest last).
const mem = new Map() // uploadId -> Blob

function memGet(id) {
  if (!mem.has(id)) return null
  const blob = mem.get(id)
  mem.delete(id)
  mem.set(id, blob) // move to most-recently-used end
  return blob
}

function memSet(id, blob) {
  if (mem.has(id)) mem.delete(id)
  mem.set(id, blob)
  if (mem.size > MEM_MAX) {
    // Evict the least-recently-used entry (first key in Map).
    mem.delete(mem.keys().next().value)
  }
}

async function diskGet(id) {
  if (!('caches' in window)) return null
  try {
    const cache = await caches.open(CACHE_NAME)
    const resp = await cache.match(CACHE_KEY_BASE + id)
    return resp ? resp.blob() : null
  } catch {
    return null
  }
}

function diskSet(id, blob) {
  if (!('caches' in window)) return
  caches.open(CACHE_NAME).then(async (cache) => {
    await cache.put(CACHE_KEY_BASE + id, new Response(blob, { headers: { 'Content-Type': blob.type } }))
    // Prune oldest entries if over limit.
    const keys = await cache.keys()
    if (keys.length > DISK_MAX) {
      for (const key of keys.slice(0, keys.length - DISK_MAX)) {
        cache.delete(key)
      }
    }
  }).catch(() => {})
}

/**
 * Fetch a thumbnail blob, using the in-memory LRU and Cache API as layered
 * caches. Returns a fresh object URL on each call — the caller is responsible
 * for revoking it when done (e.g. in a useEffect cleanup).
 */
export async function getThumb(uploadId, fetchUrl, apiKey) {
  // 1. Memory hit — fastest path.
  const memBlob = memGet(uploadId)
  if (memBlob) return URL.createObjectURL(memBlob)

  // 2. Disk hit — avoids a network round-trip on page reload.
  const diskBlob = await diskGet(uploadId)
  if (diskBlob) {
    memSet(uploadId, diskBlob)
    return URL.createObjectURL(diskBlob)
  }

  // 3. Network fetch — store result in both caches.
  const r = await fetch(fetchUrl, { headers: { 'X-Api-Key': apiKey } })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const blob = await r.blob()
  memSet(uploadId, blob)
  diskSet(uploadId, blob)
  return URL.createObjectURL(blob)
}
