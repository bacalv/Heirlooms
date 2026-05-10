import { describe, it, expect } from 'vitest'

// vitest's toEqual doesn't always deep-compare Uint8Array correctly across
// different ArrayBuffer origins; convert to plain arrays for comparisons.
function bytes(u) { return Array.from(u) }
import {
  generateMasterKey, generateDek, generateNonce, generateSalt,
  aesGcmEncrypt, aesGcmDecrypt, aesGcmEncryptWithAad,
  buildSymmetricEnvelope, parseSymmetricEnvelope,
  encryptSymmetric, decryptSymmetric, decryptStreamingContent,
  wrapDekUnderMasterKey, unwrapDekWithMasterKey,
  hkdf,
  wrapMasterKeyWithPassphrase, unwrapMasterKeyWithPassphrase,
  buildAsymmetricEnvelope, parseAsymmetricEnvelope,
  wrapMasterKeyForDevice,
  ALG_AES256GCM_V1,
  DEFAULT_ARGON2_PARAMS,
} from '../crypto/vaultCrypto'

// Use minimal Argon2 params so tests run fast.
const FAST_PARAMS = { m: 64, t: 1, p: 1 }

describe('vaultCrypto', () => {
  it('1 — generateMasterKey returns 32 bytes, two calls differ', () => {
    const a = generateMasterKey()
    const b = generateMasterKey()
    expect(a).toHaveLength(32)
    expect(bytes(a)).not.toEqual(bytes(b))
  })

  it('2 — generateDek returns 32 bytes, differs from master key', () => {
    const mk = generateMasterKey()
    const dek = generateDek()
    expect(dek).toHaveLength(32)
    expect(bytes(dek)).not.toEqual(bytes(mk))
  })

  it('3 — aesGcmEncrypt / aesGcmDecrypt round-trip', async () => {
    const key = generateMasterKey()
    const nonce = generateNonce()
    const plaintext = new TextEncoder().encode('hello heirlooms')
    const ct = await aesGcmEncrypt(key, nonce, plaintext)
    const recovered = await aesGcmDecrypt(key, nonce, ct)
    expect(bytes(recovered)).toEqual(bytes(plaintext))
  })

  it('4 — aesGcmDecrypt with wrong key throws', async () => {
    const key = generateMasterKey()
    const nonce = generateNonce()
    const ct = await aesGcmEncrypt(key, nonce, new Uint8Array([1, 2, 3]))
    const wrongKey = generateMasterKey()
    await expect(aesGcmDecrypt(wrongKey, nonce, ct)).rejects.toThrow()
  })

  it('5 — buildSymmetricEnvelope byte layout', () => {
    const alg = 'aes256gcm-v1'
    const algBytes = new TextEncoder().encode(alg)
    const nonce = generateNonce()
    const ct = new Uint8Array([10, 20, 30])
    const env = buildSymmetricEnvelope(alg, nonce, ct)

    expect(env[0]).toBe(0x01)                          // version
    expect(env[1]).toBe(algBytes.length)               // alg_id_len
    const algSlice = env.slice(2, 2 + algBytes.length)
    expect(bytes(algSlice)).toEqual(bytes(algBytes))   // alg_id
    const nonceSlice = env.slice(2 + algBytes.length, 2 + algBytes.length + 12)
    expect(bytes(nonceSlice)).toEqual(bytes(nonce))    // nonce
    const ctSlice = env.slice(2 + algBytes.length + 12)
    expect(bytes(ctSlice)).toEqual(bytes(ct))          // ciphertext
  })

  it('6 — encryptSymmetric / decryptSymmetric round-trip', async () => {
    const key = generateMasterKey()
    const msg = new TextEncoder().encode('round trip test')
    const envelope = await encryptSymmetric(ALG_AES256GCM_V1, key, msg)
    const recovered = await decryptSymmetric(envelope, key)
    expect(bytes(recovered)).toEqual(bytes(msg))
  })

  it('7 — wrapDekUnderMasterKey / unwrapDekWithMasterKey round-trip', async () => {
    const masterKey = generateMasterKey()
    const dek = generateDek()
    const wrapped = await wrapDekUnderMasterKey(dek, masterKey)
    const recovered = await unwrapDekWithMasterKey(wrapped, masterKey)
    expect(bytes(recovered)).toEqual(bytes(dek))
  })

  it('8 — decryptSymmetric with wrong key throws', async () => {
    const key = generateMasterKey()
    const envelope = await encryptSymmetric(ALG_AES256GCM_V1, key, new Uint8Array([1]))
    const wrongKey = generateMasterKey()
    await expect(decryptSymmetric(envelope, wrongKey)).rejects.toThrow()
  })

  it('9 — hkdf is deterministic for same inputs, differs for different IKMs', async () => {
    const ikm = generateMasterKey()
    const info = new TextEncoder().encode('test-info')
    const out1 = await hkdf(ikm, null, info)
    const out2 = await hkdf(ikm, null, info)
    expect(bytes(out1)).toEqual(bytes(out2))
    expect(out1).toHaveLength(32)

    const ikm2 = generateMasterKey()
    const out3 = await hkdf(ikm2, null, info)
    expect(bytes(out3)).not.toEqual(bytes(out1))
  })

  it('10 — wrapMasterKeyWithPassphrase returns envelope, salt, params', async () => {
    const masterKey = generateMasterKey()
    const result = await wrapMasterKeyWithPassphrase(masterKey, 'test-passphrase', FAST_PARAMS)
    expect(result.envelope).toBeInstanceOf(Uint8Array)
    expect(result.envelope.length).toBeGreaterThan(30)
    expect(result.salt).toHaveLength(16)
    expect(result.params).toMatchObject(FAST_PARAMS)
  })

  it('11 — unwrapMasterKeyWithPassphrase round-trip', async () => {
    const masterKey = generateMasterKey()
    const { envelope, salt, params } = await wrapMasterKeyWithPassphrase(masterKey, 'correct-horse', FAST_PARAMS)
    const recovered = await unwrapMasterKeyWithPassphrase(envelope, 'correct-horse', salt, params)
    expect(bytes(recovered)).toEqual(bytes(masterKey))
  })

  it('12 — unwrapMasterKeyWithPassphrase with wrong passphrase throws', async () => {
    const masterKey = generateMasterKey()
    const { envelope, salt, params } = await wrapMasterKeyWithPassphrase(masterKey, 'right', FAST_PARAMS)
    await expect(unwrapMasterKeyWithPassphrase(envelope, 'wrong', salt, params)).rejects.toThrow()
  })

  it('13 — buildAsymmetricEnvelope / parseAsymmetricEnvelope round-trip preserves fields', () => {
    const alg = 'p256-ecdh-hkdf-aes256gcm-v1'
    const ephemeral = new Uint8Array(65).fill(0xAB)
    const nonce = generateNonce()
    const ct = new Uint8Array([5, 6, 7, 8])
    const env = buildAsymmetricEnvelope(alg, ephemeral, nonce, ct)
    const parsed = parseAsymmetricEnvelope(env)
    expect(parsed.algorithmId).toBe(alg)
    expect(bytes(parsed.ephemeralPubkeyBytes)).toEqual(bytes(ephemeral))
    expect(bytes(parsed.nonce)).toEqual(bytes(nonce))
    expect(bytes(parsed.ciphertextWithTag)).toEqual(bytes(ct))
  })

  it('15 — decryptStreamingContent round-trips a single-chunk streaming blob', async () => {
    const dek = generateDek()
    const plaintext = new TextEncoder().encode('streaming chunk test')
    // Nonce first byte is 0x41 ('A') — valid upload-id prefix byte, never 0x01.
    const nonce = new Uint8Array(12)
    nonce[0] = 0x41
    crypto.getRandomValues(nonce.subarray(1))
    const ct = await aesGcmEncryptWithAad(dek, nonce, nonce, plaintext)
    const streamingBlob = new Uint8Array(12 + ct.length)
    streamingBlob.set(nonce, 0)
    streamingBlob.set(ct, 12)
    const recovered = await decryptStreamingContent(streamingBlob, dek)
    expect(bytes(recovered)).toEqual(bytes(plaintext))
  })

  it('14 — wrapMasterKeyForDevice ECDH round-trip decrypts correctly', async () => {
    // Generate a device keypair (recipient)
    const deviceKeypair = await crypto.subtle.generateKey(
      { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
    )
    const spki = new Uint8Array(await crypto.subtle.exportKey('spki', deviceKeypair.publicKey))

    const masterKey = generateMasterKey()
    const envelope = await wrapMasterKeyForDevice(masterKey, spki)

    // Decrypt: parse the asymmetric envelope, ECDH with device private key
    const { ephemeralPubkeyBytes, nonce, ciphertextWithTag } = parseAsymmetricEnvelope(envelope)

    const ephemeralPubKey = await crypto.subtle.importKey(
      'raw', ephemeralPubkeyBytes, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
    )
    const sharedSecret = new Uint8Array(
      await crypto.subtle.deriveBits({ name: 'ECDH', public: ephemeralPubKey }, deviceKeypair.privateKey, 256),
    )
    const { hkdf: hkdfFn, aesGcmDecrypt: dec } = await import('../crypto/vaultCrypto')
    const kek = await hkdfFn(sharedSecret, null, new TextEncoder().encode('heirlooms-v1'))
    const recovered = await dec(kek, nonce, ciphertextWithTag)
    expect(bytes(recovered)).toEqual(bytes(masterKey))
  })
})
