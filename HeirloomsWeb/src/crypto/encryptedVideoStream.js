import { API_URL } from '../api'
import { aesGcmDecryptWithAad } from './vaultCrypto'

const CIPHER_CHUNK = 4 * 1024 * 1024
const PLAIN_CHUNK  = CIPHER_CHUNK - 28

export function computeCiphertextSize(plaintextSize) {
  const n = Math.ceil(plaintextSize / PLAIN_CHUNK)
  return n * 28 + plaintextSize
}

// Open a MediaSource-backed URL for a large encrypted video.
// Returns { msUrl, cleanup } — set <video src={msUrl}>, call cleanup() on unmount.
// Strategy: use mp4box only for codec detection (onReady), then append raw decrypted
// bytes directly to the SourceBuffer. MSE accepts regular (non-fragmented) faststart
// MP4 as a contiguous byte stream: moov = init segment, mdat = media data.
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
  const MP4Box = await import('mp4box')
  const mp4boxFile = MP4Box.createFile()

  // mp4box is used only to detect the codec string via onReady.
  // We do NOT use its segmentation API — we append raw decrypted bytes to the
  // SourceBuffer directly (valid for faststart MP4 where moov precedes mdat).
  let mime = null
  mp4boxFile.onReady = (info) => {
    const vt = info.videoTracks?.[0]
    const at = info.audioTracks?.[0]
    if (!vt) return
    const withAudio = `video/mp4; codecs="${[vt.codec, at?.codec].filter(Boolean).join(',')}"`
    const videoOnly = `video/mp4; codecs="${vt.codec}"`
    mime = MediaSource.isTypeSupported(withAudio) ? withAudio
         : MediaSource.isTypeSupported(videoOnly) ? videoOnly
         : null
  }

  let sourceBuffer = null
  const pending = []  // decrypted ArrayBuffers accumulated before codec is known
  const queue   = []  // ArrayBuffers waiting while SourceBuffer.updating is true
  let allChunksFed = false

  function drainQueue() {
    if (!sourceBuffer) return
    if (sourceBuffer.updating) return
    if (queue.length > 0) {
      sourceBuffer.appendBuffer(queue.shift())
    } else if (allChunksFed && mediaSource.readyState === 'open') {
      mediaSource.endOfStream()
    }
  }

  function appendToSb(buf) {
    if (sourceBuffer.updating || queue.length > 0) queue.push(buf)
    else sourceBuffer.appendBuffer(buf)
  }

  function openSourceBuffer() {
    if (!mime || sourceBuffer) return
    sourceBuffer = mediaSource.addSourceBuffer(mime)
    sourceBuffer.addEventListener('updateend', drainQueue)
    for (const p of pending) appendToSb(p)
    pending.length = 0
  }

  const totalCipher = computeCiphertextSize(upload.fileSize)
  let cipherOff = 0
  let plainOff  = 0

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

    // Feed a copy to mp4box for codec sniffing (only until onReady fires).
    if (!mime) {
      const mpBuf = plainBuf.slice(0)
      mpBuf.fileStart = plainOff
      mp4boxFile.appendBuffer(mpBuf)
      // onReady may have fired synchronously above — open SB now if so.
      openSourceBuffer()
    }

    if (sourceBuffer) appendToSb(plainBuf)
    else pending.push(plainBuf)

    cipherOff = end + 1
    plainOff  += plainBuf.byteLength
  }

  allChunksFed = true

  // For non-faststart MP4, moov arrives in the last chunk — open SB now.
  if (!sourceBuffer) {
    mp4boxFile.flush()
    openSourceBuffer()
  }

  if (!sourceBuffer) throw new Error('Could not determine video codec')

  drainQueue()
}
