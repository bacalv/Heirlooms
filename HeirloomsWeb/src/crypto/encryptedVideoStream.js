import { API_URL } from '../api'
import { aesGcmDecryptWithAad } from './vaultCrypto'

const DEFAULT_PLAIN_CHUNK = 4 * 1024 * 1024 - 28
const PREFETCH = 4  // number of cipher chunks to fetch in parallel

export function computeCiphertextSize(plaintextSize, plainChunkSize = DEFAULT_PLAIN_CHUNK) {
  const n = Math.ceil(plaintextSize / plainChunkSize)
  return n * 28 + plaintextSize
}

// Returns one of:
//   { type: 'mse',  msUrl,   cleanup() }  — faststart: stream via MediaSource
//   { type: 'blob', blobUrl }              — non-faststart: full decrypt, ready to play
//
// Strategy: download chunk 0 first to detect faststart (moov before mdat).
// mp4box onReady fires synchronously during appendBuffer if the moov is in
// chunk 0.  If it fires → faststart → raw-byte MSE streaming (works because
// the SourceBuffer receives ftyp+moov+mdat in order).  If not → non-faststart
// → download all chunks, decrypt, return a blob URL.
export async function openEncryptedVideoStream(upload, apiKey, dek) {
  if (typeof MediaSource === 'undefined') throw new Error('MSE not supported')

  const plainChunkSize = upload.plainChunkSize ?? DEFAULT_PLAIN_CHUNK
  const cipherChunkSize = plainChunkSize + 28
  const totalCipher = computeCiphertextSize(upload.fileSize, plainChunkSize)
  const chunk0End = Math.min(cipherChunkSize, totalCipher) - 1

  // --- Chunk 0: decrypt + probe ---
  const r0 = await fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, {
    headers: { 'X-Api-Key': apiKey, 'Range': `bytes=0-${chunk0End}` },
  })
  if (!r0.ok) throw new Error(`HTTP ${r0.status}`)
  const bytes0 = new Uint8Array(await r0.arrayBuffer())
  const plain0 = await aesGcmDecryptWithAad(dek, bytes0.slice(0, 12), bytes0.slice(0, 12), bytes0.slice(12))

  // Feed a copy to mp4box to detect the codec.  onReady fires synchronously
  // here for faststart files (moov present in chunk 0).
  const MP4Box = await import('mp4box')
  const sniffer = MP4Box.createFile()
  let mime = null
  sniffer.onReady = (info) => {
    const vt = info.videoTracks?.[0]
    const at = info.audioTracks?.[0]
    if (!vt) return
    const withAudio = `video/mp4; codecs="${[vt.codec, at?.codec].filter(Boolean).join(',')}"`
    const videoOnly = `video/mp4; codecs="${vt.codec}"`
    mime = MediaSource.isTypeSupported(withAudio) ? withAudio
         : MediaSource.isTypeSupported(videoOnly) ? videoOnly
         : null
  }
  const probe = plain0.slice(0)
  probe.fileStart = 0
  sniffer.appendBuffer(probe)

  if (!mime) {
    console.log('[heirlooms] non-faststart → full download')
    return fullDownload(upload, apiKey, dek, plain0, chunk0End + 1, totalCipher, plainChunkSize)
  }

  console.log('[heirlooms] faststart → MSE streaming with', mime)
  const abort = new AbortController()
  const mediaSource = new MediaSource()
  const msUrl = URL.createObjectURL(mediaSource)

  mediaSource.addEventListener('sourceopen', () => {
    streamFaststart(upload, apiKey, dek, mime, plain0, chunk0End + 1, totalCipher, cipherChunkSize, mediaSource, abort.signal)
      .catch(() => { if (mediaSource.readyState === 'open') mediaSource.endOfStream('network') })
  }, { once: true })

  return { type: 'mse', msUrl, cleanup() { abort.abort(); URL.revokeObjectURL(msUrl) } }
}

async function streamFaststart(upload, apiKey, dek, mime, plain0, startCipherOff, totalCipher, cipherChunkSize, mediaSource, signal) {
  const sb = mediaSource.addSourceBuffer(mime)
  const queue = []
  let allFed = false

  function drain() {
    if (sb.updating) return
    if (queue.length > 0) sb.appendBuffer(queue.shift())
    else if (allFed && mediaSource.readyState === 'open') mediaSource.endOfStream()
  }

  function append(buf) {
    if (sb.updating || queue.length > 0) queue.push(buf)
    else sb.appendBuffer(buf)
  }

  sb.addEventListener('updateend', drain)
  sb.addEventListener('error', e => console.error('[heirlooms] SB error:', e))

  append(plain0)

  // Build list of remaining chunk ranges
  const chunks = []
  let o = startCipherOff
  while (o < totalCipher) {
    const end = Math.min(o + cipherChunkSize, totalCipher) - 1
    chunks.push({ off: o, end })
    o = end + 1
  }

  // Fetch chunk bytes and decrypt — returns Uint8Array
  async function fetchChunk({ off, end }) {
    const r = await fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, {
      headers: { 'X-Api-Key': apiKey, 'Range': `bytes=${off}-${end}` },
      signal,
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const bytes = new Uint8Array(await r.arrayBuffer())
    return aesGcmDecryptWithAad(dek, bytes.slice(0, 12), bytes.slice(0, 12), bytes.slice(12))
  }

  // Parallel sliding-window: keep PREFETCH fetches in flight, append in order
  let fetchIdx = 0
  let appendIdx = 0
  const inFlight = new Map()

  function seedFetches() {
    while (fetchIdx < chunks.length && inFlight.size < PREFETCH) {
      inFlight.set(fetchIdx, fetchChunk(chunks[fetchIdx]))
      fetchIdx++
    }
  }

  seedFetches()

  while (appendIdx < chunks.length) {
    if (signal.aborted) return
    const plain = await inFlight.get(appendIdx)
    inFlight.delete(appendIdx)
    appendIdx++
    append(plain)
    seedFetches()
  }

  allFed = true
  drain()
}

async function fullDownload(upload, apiKey, dek, plain0, startCipherOff, totalCipher, plainChunkSize) {
  const cipherChunkSize = plainChunkSize + 28
  const parts = [plain0]
  let totalBytes = plain0.byteLength
  let off = startCipherOff

  // Parallel sliding-window for the full download too
  const chunks = []
  while (off < totalCipher) {
    const end = Math.min(off + cipherChunkSize, totalCipher) - 1
    chunks.push({ off, end })
    off = end + 1
  }

  async function fetchChunk({ off: o, end }) {
    const r = await fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, {
      headers: { 'X-Api-Key': apiKey, 'Range': `bytes=${o}-${end}` },
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const bytes = new Uint8Array(await r.arrayBuffer())
    return aesGcmDecryptWithAad(dek, bytes.slice(0, 12), bytes.slice(0, 12), bytes.slice(12))
  }

  let fetchIdx = 0
  let appendIdx = 0
  const inFlight = new Map()

  function seedFetches() {
    while (fetchIdx < chunks.length && inFlight.size < PREFETCH) {
      inFlight.set(fetchIdx, fetchChunk(chunks[fetchIdx]))
      fetchIdx++
    }
  }

  seedFetches()

  while (appendIdx < chunks.length) {
    const plain = await inFlight.get(appendIdx)
    inFlight.delete(appendIdx)
    appendIdx++
    parts.push(plain)
    totalBytes += plain.byteLength
    seedFetches()
  }

  const combined = new Uint8Array(totalBytes)
  let pos = 0
  for (const p of parts) { combined.set(new Uint8Array(p), pos); pos += p.byteLength }

  return { type: 'blob', blobUrl: URL.createObjectURL(new Blob([combined], { type: 'video/mp4' })) }
}
