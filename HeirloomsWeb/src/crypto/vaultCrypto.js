import { argon2id } from '@noble/hashes/argon2'

export const ALG_AES256GCM_V1          = 'aes256gcm-v1'
export const ALG_MASTER_AES256GCM_V1   = 'master-aes256gcm-v1'
export const ALG_PLOT_AES256GCM_V1     = 'plot-aes256gcm-v1'
export const ALG_ARGON2ID_AES256GCM_V1 = 'argon2id-aes256gcm-v1'
export const ALG_P256_ECDH_HKDF_V1     = 'p256-ecdh-hkdf-aes256gcm-v1'

export function generateMasterKey() {
  const b = new Uint8Array(32); crypto.getRandomValues(b); return b
}

export function generateDek() {
  const b = new Uint8Array(32); crypto.getRandomValues(b); return b
}

export function generateNonce() {
  const b = new Uint8Array(12); crypto.getRandomValues(b); return b
}

export function generateSalt(size = 16) {
  const b = new Uint8Array(size); crypto.getRandomValues(b); return b
}

export async function aesGcmEncrypt(key, nonce, plaintext) {
  const ck = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt'])
  const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce, tagLength: 128 }, ck, plaintext)
  return new Uint8Array(ct)
}

export async function aesGcmEncryptWithAad(key, nonce, aad, plaintext) {
  const ck = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt'])
  const ct = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce, tagLength: 128, additionalData: aad },
    ck, plaintext,
  )
  return new Uint8Array(ct)
}

export async function aesGcmDecrypt(key, nonce, ciphertextWithTag) {
  const ck = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt'])
  try {
    const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce, tagLength: 128 }, ck, ciphertextWithTag)
    return new Uint8Array(pt)
  } catch (e) {
    throw new Error(`AES-GCM decryption failed: ${e.message}`)
  }
}

export async function aesGcmDecryptWithAad(key, nonce, aad, ctWithTag) {
  const ck = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt'])
  return crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce, additionalData: aad }, ck, ctWithTag)
}

// Decrypt streaming-encrypted content.
// Format: sequence of [nonce(12)][ciphertext+tag] cipher chunks, each plainChunkSize+28 bytes except the last.
// plainChunkSize defaults to the original 4 MiB - 28 format for backward compatibility.
export async function decryptStreamingContent(encryptedBytes, dek, plainChunkSize = 4 * 1024 * 1024 - 28) {
  const CHUNK_SIZE = plainChunkSize + 28
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

// Symmetric envelope:
// [1] version=0x01  [1] alg_id_len  [N] alg_id  [12] nonce  [V] ciphertext+tag

export function buildSymmetricEnvelope(algorithmId, nonce, ct) {
  const alg = new TextEncoder().encode(algorithmId)
  const out = new Uint8Array(1 + 1 + alg.length + 12 + ct.length)
  let i = 0
  out[i++] = 0x01
  out[i++] = alg.length
  out.set(alg, i); i += alg.length
  out.set(nonce, i); i += 12
  out.set(ct, i)
  return out
}

export function parseSymmetricEnvelope(envelope) {
  let i = 0
  const version = envelope[i++]
  if (version !== 0x01) throw new Error(`Unknown envelope version: ${version}`)
  const algLen = envelope[i++]
  const algorithmId = new TextDecoder().decode(envelope.slice(i, i + algLen)); i += algLen
  const nonce = envelope.slice(i, i + 12); i += 12
  const ciphertextWithTag = envelope.slice(i)
  return { algorithmId, nonce, ciphertextWithTag }
}

export async function encryptSymmetric(algorithmId, key, plaintext) {
  const nonce = generateNonce()
  const ct = await aesGcmEncrypt(key, nonce, plaintext)
  return buildSymmetricEnvelope(algorithmId, nonce, ct)
}

export async function decryptSymmetric(envelope, key) {
  const { nonce, ciphertextWithTag } = parseSymmetricEnvelope(envelope)
  return aesGcmDecrypt(key, nonce, ciphertextWithTag)
}

export async function wrapDekUnderMasterKey(dek, masterKey) {
  return encryptSymmetric(ALG_MASTER_AES256GCM_V1, masterKey, dek)
}

export async function unwrapDekWithMasterKey(envelope, masterKey) {
  return decryptSymmetric(envelope, masterKey)
}

export async function hkdf(ikm, salt = null, info = new Uint8Array(0)) {
  const key = await crypto.subtle.importKey('raw', ikm, 'HKDF', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt: salt ?? new Uint8Array(32), info },
    key, 256,
  )
  return new Uint8Array(bits)
}

export const DEFAULT_ARGON2_PARAMS = { m: 65536, t: 3, p: 1 }

export async function wrapMasterKeyWithPassphrase(masterKey, passphrase, params = DEFAULT_ARGON2_PARAMS) {
  const salt = generateSalt(16)
  const pw = new TextEncoder().encode(passphrase)
  await new Promise(resolve => setTimeout(resolve, 0))
  const kek = argon2id(pw, salt, { m: params.m, t: params.t, p: params.p, dkLen: 32 })
  const envelope = await encryptSymmetric(ALG_ARGON2ID_AES256GCM_V1, kek, masterKey)
  return { envelope, salt, params }
}

export async function unwrapMasterKeyWithPassphrase(envelope, passphrase, salt, params) {
  const pw = new TextEncoder().encode(passphrase)
  // Yield so any pending setState re-render (e.g. "Unlocking…") fires before
  // the synchronous Argon2id KDF blocks the main thread.
  await new Promise(resolve => setTimeout(resolve, 0))
  const kek = argon2id(pw, salt, { m: params.m, t: params.t, p: params.p, dkLen: 32 })
  try {
    return await decryptSymmetric(envelope, kek)
  } catch {
    throw new Error('Incorrect passphrase')
  }
}

// Asymmetric envelope:
// [1] version=0x01  [1] alg_id_len  [N] alg_id  [65] ephemeral_pubkey  [12] nonce  [V] ct+tag

export function buildAsymmetricEnvelope(algorithmId, ephemeralPubkeyBytes, nonce, ct) {
  const alg = new TextEncoder().encode(algorithmId)
  const out = new Uint8Array(1 + 1 + alg.length + 65 + 12 + ct.length)
  let i = 0
  out[i++] = 0x01
  out[i++] = alg.length
  out.set(alg, i); i += alg.length
  out.set(ephemeralPubkeyBytes, i); i += 65
  out.set(nonce, i); i += 12
  out.set(ct, i)
  return out
}

export function parseAsymmetricEnvelope(envelope) {
  let i = 0
  const version = envelope[i++]
  if (version !== 0x01) throw new Error(`Unknown envelope version: ${version}`)
  const algLen = envelope[i++]
  const algorithmId = new TextDecoder().decode(envelope.slice(i, i + algLen)); i += algLen
  const ephemeralPubkeyBytes = envelope.slice(i, i + 65); i += 65
  const nonce = envelope.slice(i, i + 12); i += 12
  const ciphertextWithTag = envelope.slice(i)
  return { algorithmId, ephemeralPubkeyBytes, nonce, ciphertextWithTag }
}

export async function wrapMasterKeyForDevice(masterKey, devicePubkeySpki) {
  const ephemeral = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
  )
  const ephemeralPubkeyBytes = new Uint8Array(
    await crypto.subtle.exportKey('raw', ephemeral.publicKey),
  )
  const recipientKey = await crypto.subtle.importKey(
    'spki', devicePubkeySpki, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
  )
  const sharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits({ name: 'ECDH', public: recipientKey }, ephemeral.privateKey, 256),
  )
  const kek = await hkdf(sharedSecret, null, new TextEncoder().encode('heirlooms-v1'))
  const nonce = generateNonce()
  const ct = await aesGcmEncrypt(kek, nonce, masterKey)
  return buildAsymmetricEnvelope(ALG_P256_ECDH_HKDF_V1, ephemeralPubkeyBytes, nonce, ct)
}

export async function unwrapMasterKeyForDevice(envelope, devicePrivateKey) {
  const { ephemeralPubkeyBytes, nonce, ciphertextWithTag } = parseAsymmetricEnvelope(envelope)
  const ephemeralPubkey = await crypto.subtle.importKey(
    'raw', ephemeralPubkeyBytes, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
  )
  const sharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits({ name: 'ECDH', public: ephemeralPubkey }, devicePrivateKey, 256),
  )
  const kek = await hkdf(sharedSecret, null, new TextEncoder().encode('heirlooms-v1'))
  return aesGcmDecrypt(kek, nonce, ciphertextWithTag)
}

// Wrap a DEK under a friend's sharing public key (raw 65-byte uncompressed P-256 point).
// Produces an asymmetric envelope with dekFormat = ALG_P256_ECDH_HKDF_V1.
export async function wrapDekForFriend(dek, friendPubkeySpkiBytes) {
  const ephemeral = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
  )
  const ephemeralPubkeyBytes = new Uint8Array(
    await crypto.subtle.exportKey('raw', ephemeral.publicKey),
  )
  const recipientKey = await crypto.subtle.importKey(
    'spki', friendPubkeySpkiBytes, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
  )
  const sharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits({ name: 'ECDH', public: recipientKey }, ephemeral.privateKey, 256),
  )
  const kek = await hkdf(sharedSecret, null, new TextEncoder().encode('heirlooms-v1'))
  const nonce = generateNonce()
  const ct = await aesGcmEncrypt(kek, nonce, dek)
  return buildAsymmetricEnvelope(ALG_P256_ECDH_HKDF_V1, ephemeralPubkeyBytes, nonce, ct)
}

export async function importSharingPrivkey(pkcs8Bytes) {
  return crypto.subtle.importKey(
    'pkcs8', pkcs8Bytes, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits'],
  )
}

export async function unwrapWithSharingKey(envelope, sharingPrivkey) {
  return unwrapMasterKeyForDevice(envelope, sharingPrivkey)
}

// ---- Plot key (M10 E3) ------------------------------------------------------

export function generatePlotKey() {
  const b = new Uint8Array(32)
  crypto.getRandomValues(b)
  return b
}

// Wrap a raw plot key under a member's sharing pubkey (SPKI bytes).
// Returns { wrappedKey: Uint8Array, format: 'p256-ecdh-hkdf-aes256gcm-v1' }
export async function wrapPlotKeyForMember(plotKeyBytes, memberSharingPubkeySpki) {
  const wrapped = await wrapMasterKeyForDevice(plotKeyBytes, memberSharingPubkeySpki)
  return { wrappedKey: wrapped, format: ALG_P256_ECDH_HKDF_V1 }
}

// Unwrap a wrapped plot key using own sharing private key.
// Returns raw 32-byte plot key.
export async function unwrapPlotKey(wrappedPlotKey, sharingPrivkey) {
  return unwrapWithSharingKey(wrappedPlotKey, sharingPrivkey)
}

// Wrap a DEK under a plot key (AES-256-GCM symmetric wrap).
// Returns { wrappedDek: Uint8Array, format: 'plot-aes256gcm-v1' }
export async function wrapDekWithPlotKey(dekBytes, plotKeyBytes) {
  const wrapped = await encryptSymmetric(ALG_PLOT_AES256GCM_V1, plotKeyBytes, dekBytes)
  return { wrappedDek: wrapped, format: ALG_PLOT_AES256GCM_V1 }
}

// Unwrap a DEK that was wrapped with a plot key.
export async function unwrapDekWithPlotKey(wrappedDek, plotKeyBytes) {
  return decryptSymmetric(wrappedDek, plotKeyBytes)
}

export function toB64(bytes) {
  let s = ''
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i])
  return btoa(s)
}

export function fromB64(str) {
  return Uint8Array.from(atob(str), c => c.charCodeAt(0))
}

export function toB64url(bytes) {
  return toB64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

export function fromB64url(str) {
  const padded = str.replace(/-/g, '+').replace(/_/g, '/').padEnd(str.length + (4 - str.length % 4) % 4, '=')
  return fromB64(padded)
}

export async function sha256(bytes) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
}
