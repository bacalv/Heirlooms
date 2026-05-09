import { argon2id } from '@noble/hashes/argon2'

export const ALG_AES256GCM_V1         = 'aes256gcm-v1'
export const ALG_MASTER_AES256GCM_V1  = 'master-aes256gcm-v1'
export const ALG_ARGON2ID_AES256GCM_V1 = 'argon2id-aes256gcm-v1'
export const ALG_P256_ECDH_HKDF_V1    = 'p256-ecdh-hkdf-aes256gcm-v1'

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

export async function aesGcmDecrypt(key, nonce, ciphertextWithTag) {
  const ck = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt'])
  try {
    const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce, tagLength: 128 }, ck, ciphertextWithTag)
    return new Uint8Array(pt)
  } catch (e) {
    throw new Error(`AES-GCM decryption failed: ${e.message}`)
  }
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
  const kek = argon2id(pw, salt, { m: params.m, t: params.t, p: params.p, dkLen: 32 })
  const envelope = await encryptSymmetric(ALG_ARGON2ID_AES256GCM_V1, kek, masterKey)
  return { envelope, salt, params }
}

export async function unwrapMasterKeyWithPassphrase(envelope, passphrase, salt, params) {
  const pw = new TextEncoder().encode(passphrase)
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

export function toB64(bytes) {
  let s = ''
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i])
  return btoa(s)
}

export function fromB64(str) {
  return Uint8Array.from(atob(str), c => c.charCodeAt(0))
}
