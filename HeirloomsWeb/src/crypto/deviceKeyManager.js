const DB_NAME    = 'heirlooms-vault'
const DB_VERSION = 1
const STORE      = 'keys'
const K_PRIVATE  = 'devicePrivateKey'
const K_PUBLIC   = 'devicePublicKey'
const LS_DEVICE_ID   = 'heirlooms-deviceId'
const LS_VAULT_SETUP = 'heirlooms-vaultSetUp'

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = (e) => e.target.result.createObjectStore(STORE)
    req.onsuccess = (e) => resolve(e.target.result)
    req.onerror = () => reject(req.error)
  })
}

function idbGet(db, key) {
  return new Promise((resolve, reject) => {
    const req = db.transaction(STORE, 'readonly').objectStore(STORE).get(key)
    req.onsuccess = () => resolve(req.result ?? null)
    req.onerror = () => reject(req.error)
  })
}

function idbPut(db, key, value) {
  return new Promise((resolve, reject) => {
    const req = db.transaction(STORE, 'readwrite').objectStore(STORE).put(value, key)
    req.onsuccess = () => resolve()
    req.onerror = () => reject(req.error)
  })
}

export async function isDeviceRegistered() {
  try {
    const db = await openDb()
    const key = await idbGet(db, K_PRIVATE)
    return key !== null
  } catch {
    return false
  }
}

// Generates a fresh P-256 keypair, stores both CryptoKey objects in IndexedDB,
// writes a stable deviceId UUID to localStorage, and returns the public key as
// SPKI DER bytes for server registration.
export async function generateAndStoreKeypair() {
  const keypair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    false,           // private key non-extractable
    ['deriveBits'],
  )
  const db = await openDb()
  await idbPut(db, K_PRIVATE, keypair.privateKey)
  await idbPut(db, K_PUBLIC, keypair.publicKey)

  if (!localStorage.getItem(LS_DEVICE_ID)) {
    localStorage.setItem(LS_DEVICE_ID, crypto.randomUUID())
  }

  return new Uint8Array(await crypto.subtle.exportKey('spki', keypair.publicKey))
}

export async function loadPrivateKey() {
  try {
    const db = await openDb()
    return await idbGet(db, K_PRIVATE)
  } catch {
    return null
  }
}

export async function loadPublicKeySpki() {
  try {
    const db = await openDb()
    const pubKey = await idbGet(db, K_PUBLIC)
    if (!pubKey) return null
    return new Uint8Array(await crypto.subtle.exportKey('spki', pubKey))
  } catch {
    return null
  }
}

export function getDeviceId() {
  return localStorage.getItem(LS_DEVICE_ID)
}

export function getDeviceLabel() {
  const label = `${navigator.vendor} ${navigator.platform}`.trim()
  return label || 'Web browser'
}

export function isVaultSetUp() {
  return localStorage.getItem(LS_VAULT_SETUP) === 'true'
}

export function markVaultSetUp() {
  localStorage.setItem(LS_VAULT_SETUP, 'true')
}
