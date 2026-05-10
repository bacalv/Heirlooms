import { API_URL } from '../api'
import { aesGcmDecryptWithAad } from './vaultCrypto'

const CIPHER_CHUNK = 4 * 1024 * 1024
const PLAIN_CHUNK  = CIPHER_CHUNK - 28

export function computeCiphertextSize(plaintextSize) {
  const n = Math.ceil(plaintextSize / PLAIN_CHUNK)
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
// → download all chunks, decrypt, return a blob URL (same latency as Fix 1,
// guaranteed correct).
//
// We deliberately avoid mp4box's segmentation API (setSegmentOptions / start /
// onSegment) because initializeSegmentation() calls resetTables() which
// deletes trak.samples, leaving start() with no samples to emit.
export async function openEncryptedVideoStream(upload, apiKey, dek) {
  if (typeof MediaSource === 'undefined') throw new Error('MSE not supported')

  const totalCipher = computeCiphertextSize(upload.fileSize)
  const chunk0End = Math.min(CIPHER_CHUNK, totalCipher) - 1

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
    console.log('[heirlooms] chunk-0 probe — codec:', vt.codec, at?.codec ?? '(no audio)', '→', mime)
  }
  const probe = plain0.slice(0)   // copy so plain0 stays intact
  probe.fileStart = 0
  sniffer.appendBuffer(probe)

  if (!mime) {
    // Non-faststart (moov not in chunk 0): full download then blob URL.
    console.log('[heirlooms] non-faststart → full download')
    return fullDownload(upload, apiKey, dek, plain0, chunk0End + 1, totalCipher)
  }

  // Faststart: stream raw decrypted bytes into a SourceBuffer.
  console.log('[heirlooms] faststart → MSE streaming with', mime)
  const abort = new AbortController()
  const mediaSource = new MediaSource()
  const msUrl = URL.createObjectURL(mediaSource)

  mediaSource.addEventListener('sourceopen', () => {
    streamFaststart(upload, apiKey, dek, mime, plain0, chunk0End + 1, totalCipher, mediaSource, abort.signal)
      .catch(() => { if (mediaSource.readyState === 'open') mediaSource.endOfStream('network') })
  }, { once: true })

  return { type: 'mse', msUrl, cleanup() { abort.abort(); URL.revokeObjectURL(msUrl) } }
}

// Appends chunk 0 (already decrypted) then continues fetching from startCipherOff.
async function streamFaststart(upload, apiKey, dek, mime, plain0, startCipherOff, totalCipher, mediaSource, signal) {
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

  append(plain0)  // ftyp + moov + start of mdat

  let off = startCipherOff
  while (off < totalCipher) {
    if (signal.aborted) return
    const end = Math.min(off + CIPHER_CHUNK, totalCipher) - 1
    const r = await fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, {
      headers: { 'X-Api-Key': apiKey, 'Range': `bytes=${off}-${end}` },
      signal,
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const bytes = new Uint8Array(await r.arrayBuffer())
    const plain = await aesGcmDecryptWithAad(dek, bytes.slice(0, 12), bytes.slice(0, 12), bytes.slice(12))
    append(plain)
    off = end + 1
  }

  allFed = true
  drain()
}

// Downloads all chunks (chunk 0 already decrypted), combines, returns a blob URL.
async function fullDownload(upload, apiKey, dek, plain0, startCipherOff, totalCipher) {
  const parts = [plain0]
  let totalBytes = plain0.byteLength
  let off = startCipherOff

  while (off < totalCipher) {
    const end = Math.min(off + CIPHER_CHUNK, totalCipher) - 1
    const r = await fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, {
      headers: { 'X-Api-Key': apiKey, 'Range': `bytes=${off}-${end}` },
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const bytes = new Uint8Array(await r.arrayBuffer())
    const plain = await aesGcmDecryptWithAad(dek, bytes.slice(0, 12), bytes.slice(0, 12), bytes.slice(12))
    parts.push(plain)
    totalBytes += plain.byteLength
    off = end + 1
  }

  const combined = new Uint8Array(totalBytes)
  let pos = 0
  for (const p of parts) { combined.set(new Uint8Array(p), pos); pos += p.byteLength }

  return { type: 'blob', blobUrl: URL.createObjectURL(new Blob([combined], { type: upload.mimeType })) }
}
