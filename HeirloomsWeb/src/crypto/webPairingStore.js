const DB_NAME    = 'heirlooms_pairing'
const DB_VERSION = 1
const STORE_NAME = 'pairing'
const RECORD_KEY = 'current'

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = e => e.target.result.createObjectStore(STORE_NAME)
    req.onsuccess = e => resolve(e.target.result)
    req.onerror = e => reject(e.target.error)
  })
}

// Persists the pairing keypair and wrapped master key in IDB.
// Non-extractable WebCrypto keys survive structuredClone into IDB without
// ever becoming extractable — standard pattern for refresh safety.
export async function savePairingMaterial({ privateKey, publicKeyRaw, wrappedMasterKey, wrapFormat }) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).put({ privateKey, publicKeyRaw, wrappedMasterKey, wrapFormat }, RECORD_KEY)
    tx.oncomplete = () => { db.close(); resolve() }
    tx.onerror = e => { db.close(); reject(e.target.error) }
  })
}

export async function loadPairingMaterial() {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const req = tx.objectStore(STORE_NAME).get(RECORD_KEY)
    req.onsuccess = e => { db.close(); resolve(e.target.result ?? null) }
    req.onerror = e => { db.close(); reject(e.target.error) }
  })
}

export async function clearPairingMaterial() {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).delete(RECORD_KEY)
    tx.oncomplete = () => { db.close(); resolve() }
    tx.onerror = e => { db.close(); reject(e.target.error) }
  })
}
