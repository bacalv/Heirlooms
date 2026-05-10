import { API_URL } from '../api'
import { aesGcmDecryptWithAad } from './vaultCrypto'

const CIPHER_CHUNK = 4 * 1024 * 1024   // ciphertext chunk: 12 nonce + plaintext + 16 tag
const PLAIN_CHUNK  = CIPHER_CHUNK - 28  // max plaintext bytes per chunk

export function computeCiphertextSize(plaintextSize) {
  const n = Math.ceil(plaintextSize / PLAIN_CHUNK)
  return n * 28 + plaintextSize
}

// Open a MediaSource-backed URL for a large encrypted video.
// Returns { msUrl, cleanup } where cleanup() revokes the URL and aborts fetching.
// The MediaSource URL can be set as <video src> immediately; playback starts
// after the first few MP4 segments are decrypted and buffered.
export async function openEncryptedVideoStream(upload, apiKey, dek) {
  if (typeof MediaSource === 'undefined') throw new Error('MSE not supported')

  const abort = new AbortController()
  const mediaSource = new MediaSource()
  const msUrl = URL.createObjectURL(mediaSource)

  mediaSource.addEventListener('sourceopen', () => {
    runStream(upload, apiKey, dek, mediaSource, abort.signal).catch(() => {
      if (mediaSource.readyState === 'open') mediaSource.endOfStream('network')
    })
  }, { once: true })

  return {
    msUrl,
    cleanup() { abort.abort(); URL.revokeObjectURL(msUrl) },
  }
}

async function runStream(upload, apiKey, dek, mediaSource, signal) {
  const { default: MP4Box } = await import('mp4box')
  const mp4boxFile = MP4Box.createFile()

  const sourceBuffers = {}  // trackId → SourceBuffer
  const queues = {}         // trackId → ArrayBuffer[]
  let allChunksFed = false
  let pendingEos = false

  function tryEndOfStream() {
    if (!pendingEos) return
    if (mediaSource.readyState !== 'open') return
    const allIdle = Object.values(sourceBuffers).every(sb => !sb.updating)
    const allDrained = Object.values(queues).every(q => q.length === 0)
    if (allIdle && allDrained) mediaSource.endOfStream()
  }

  function drainQueue(id) {
    const sb = sourceBuffers[id]
    const q = queues[id]
    if (!sb || !q || sb.updating) return
    if (q.length > 0) sb.appendBuffer(q.shift())
    else tryEndOfStream()
  }

  function enqueue(id, buffer) {
    const sb = sourceBuffers[id]
    const q = queues[id]
    if (sb.updating || q.length > 0) q.push(buffer)
    else sb.appendBuffer(buffer)
  }

  mp4boxFile.onReady = (info) => {
    const tracks = [...(info.videoTracks || []), ...(info.audioTracks || [])]

    for (const track of tracks) {
      const mime = `${track.type === 'video' ? 'video' : 'audio'}/mp4; codecs="${track.codec}"`
      if (!MediaSource.isTypeSupported(mime)) continue
      const sb = mediaSource.addSourceBuffer(mime)
      sourceBuffers[track.id] = sb
      queues[track.id] = []
      sb.addEventListener('updateend', () => drainQueue(track.id))
      mp4boxFile.setSegmentOptions(track.id, sb, { nbSamples: 200 })
    }

    for (const seg of mp4boxFile.initializeSegmentation()) {
      seg.user.appendBuffer(seg.buffer)
    }

    mp4boxFile.start()
  }

  mp4boxFile.onSegment = (id, user, buffer, _sampleNum, is_last) => {
    enqueue(id, buffer)
    if (is_last && allChunksFed) {
      pendingEos = true
      tryEndOfStream()
    }
  }

  const totalCipher = computeCiphertextSize(upload.fileSize)
  let cipherOff = 0
  let plainOff = 0

  while (cipherOff < totalCipher) {
    if (signal.aborted) return
    const end = Math.min(cipherOff + CIPHER_CHUNK, totalCipher) - 1
    const r = await fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, {
      headers: { 'X-Api-Key': apiKey, 'Range': `bytes=${cipherOff}-${end}` },
      signal,
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)

    const chunk = new Uint8Array(await r.arrayBuffer())
    const nonce = chunk.slice(0, 12)
    const plainBuf = await aesGcmDecryptWithAad(dek, nonce, nonce, chunk.slice(12))

    plainBuf.fileStart = plainOff
    mp4boxFile.appendBuffer(plainBuf)

    cipherOff = end + 1
    plainOff += plainBuf.byteLength
  }

  allChunksFed = true
  mp4boxFile.flush()
  pendingEos = true
  tryEndOfStream()
}
