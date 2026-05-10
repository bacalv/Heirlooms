# SE Brief: Encrypted video playback on web

**Date:** 10 May 2026
**Type:** Web only. No server changes.

---

## Background

Android's encrypted video playback was fixed in this session (commits `50e1073`,
`1e4e091`, `1dfb084`). The web app has the same underlying bug and needs the same
correctness fix. A follow-on streaming improvement is also possible.

---

## Bug: large encrypted videos never play on web

`PhotoDetailPage.jsx` (`loadContent`, line 421) handles all encrypted uploads with
a single path: fetch full file → `decryptSymmetric` → blob URL. This breaks for
large files (> 10 MB) because they are encrypted with the **streaming chunk format**
(sequence of `[nonce(12)][ciphertext+tag]` 4 MiB blocks with no envelope header),
not the symmetric envelope format that `decryptSymmetric` expects.

`decryptSymmetric` in `vaultCrypto.js` calls `parseSymmetricEnvelope` which expects
the first byte to be envelope version `0x01`. The streaming format's first byte is
the first byte of the chunk nonce (derived from the printable `storageKey` string —
never `0x01`). The parse throws, the promise rejects, `blobUrl` is never set, and
the video shows as a blank spinner indefinitely.

Small encrypted images and small encrypted videos (< 10 MB, envelope format) are
**not affected** — they continue to work.

---

## Fix 1 (required): add `decryptStreamingContent` to `vaultCrypto.js`

### `vaultCrypto.js` — add new export

```js
// Decrypt streaming-encrypted content (produced by encryptAndUploadStreaming).
// Format: sequence of [nonce(12)][ciphertext+tag] chunks, each exactly 4 MiB
// except the last. AAD for each chunk equals its nonce (same bytes).
export async function decryptStreamingContent(encryptedBytes, dek) {
  const CHUNK_SIZE = 4 * 1024 * 1024   // 4 MiB ciphertext chunk
  const NONCE_SIZE = 12
  const parts = []
  let offset = 0
  while (offset < encryptedBytes.length) {
    const chunkEnd = Math.min(offset + CHUNK_SIZE, encryptedBytes.length)
    const nonce = encryptedBytes.slice(offset, offset + NONCE_SIZE)
    const ctWithTag = encryptedBytes.slice(offset + NONCE_SIZE, chunkEnd)
    const plain = await aesGcmDecryptWithAad(dek, nonce, nonce, ctWithTag)
    parts.push(plain)
    offset = chunkEnd
  }
  const total = parts.reduce((n, p) => n + p.byteLength, 0)
  const out = new Uint8Array(total)
  let pos = 0
  for (const p of parts) { out.set(new Uint8Array(p), pos); pos += p.byteLength }
  return out
}
```

`aesGcmDecryptWithAad` already exists in `vaultCrypto.js` (used for the streaming
encrypt path). If it's not exported, either export it or inline the WebCrypto call:

```js
async function aesGcmDecryptWithAad(key, nonce, aad, ctWithTag) {
  const cryptoKey = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt'])
  return crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce, additionalData: aad }, cryptoKey, ctWithTag)
}
```

### `PhotoDetailPage.jsx` — format detection

Replace the `decryptSymmetric` call in `loadContent` with format detection:

```js
const encBytes = new Uint8Array(await r.arrayBuffer())
// Envelope format always starts with version byte 0x01.
// Streaming chunk format starts with the nonce's first byte (printable ASCII, never 0x01).
const plainBytes = encBytes[0] === 0x01
  ? await decryptSymmetric(encBytes, dek)
  : await decryptStreamingContent(encBytes, dek)
const blob = new Blob([plainBytes], { type: upload.mimeType })
```

Also import `decryptStreamingContent` at the top of the file.

---

## Fix 2 (optional): streaming playback via MediaSource Extensions

Fix 1 still downloads the entire encrypted file before playback can start. For a
30 MB video on mobile web this is a significant wait. The browser equivalent of
Android's `DecryptingDataSource` is the
[MediaSource Extensions (MSE) API](https://developer.mozilla.org/en-US/docs/Web/API/MediaSource):

1. Create a `MediaSource` and attach it to a `<video>` as `src`.
2. Open a `SourceBuffer` for the video's MIME type.
3. Fetch the encrypted content using the server's Range request support
   (added in `1e4e091` — the `/file` endpoint now returns 206).
4. Decrypt each 4 MiB chunk as it arrives and `appendBuffer` the plaintext.

This allows ExoPlayer-like progressive playback on the web: video starts after the
first one or two chunks are decrypted rather than after the whole file downloads.

MSE requires careful handling of:
- `SourceBuffer.updating` — can only append when not updating
- MIME type with codec string, e.g. `video/mp4; codecs="avc1.42E01E,mp4a.40.2"`
  (needs to be known at startup; may require sniffing or storing at upload time)
- Seeking: translate plaintext seek offset to ciphertext chunk boundary via Range request
- Error recovery and `endOfStream()`

Given the complexity, Fix 2 should be a separate brief/increment. Fix 1 alone
unblocks the feature for users willing to wait for the download.

---

## Files to change

| File | Change |
|---|---|
| `HeirloomsWeb/src/crypto/vaultCrypto.js` | Add `decryptStreamingContent` (and `aesGcmDecryptWithAad` if needed) |
| `HeirloomsWeb/src/pages/PhotoDetailPage.jsx` | Format detection + call `decryptStreamingContent` for streaming-format files |

No server changes needed — Range request support is already deployed.

---

## Acceptance criteria

1. Opening an encrypted video > 10 MB in the web app shows the video (after
   downloading the full file) rather than a blank spinner.
2. Opening an encrypted image or encrypted video < 10 MB is unaffected.
3. All existing web integration tests pass.
