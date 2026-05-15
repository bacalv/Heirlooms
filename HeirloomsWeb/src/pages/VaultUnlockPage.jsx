import { useEffect, useRef, useState } from 'react'
import { OliveBranchIcon } from '../brand/OliveBranchIcon'
import { WorkingDots } from '../brand/WorkingDots'
import { API_URL, putSharingKey } from '../api'
import * as vault from '../crypto/vaultCrypto'
import * as vaultSession from '../crypto/vaultSession'
import * as deviceKeyManager from '../crypto/deviceKeyManager'

async function loadSharingKey(apiKey, masterKey) {
  try {
    const r = await fetch(`${API_URL}/api/keys/sharing/me`, { headers: { 'X-Api-Key': apiKey } })
    if (!r.ok) return
    const { wrappedPrivkey } = await r.json()
    if (!wrappedPrivkey) return
    const pkcs8Bytes = await vault.unwrapDekWithMasterKey(vault.fromB64(wrappedPrivkey), masterKey)
    const cryptoKey = await vault.importSharingPrivkey(pkcs8Bytes)
    vaultSession.setSharingPrivkey(cryptoKey)
  } catch { /* sharing key not set up yet — fine */ }
}

// Generates and uploads a sharing keypair if none exists yet.
// Safe to call after loadSharingKey — skips generation if the key is already loaded.
async function ensureSharingKey(apiKey, masterKey) {
  if (vaultSession.getSharingPrivkey() !== null) return
  try {
    const { privkeyPkcs8, pubkeySpki } = await vault.generateSharingKeypair()
    const wrappedPrivkey = await vault.wrapDekUnderMasterKey(privkeyPkcs8, masterKey)
    await putSharingKey(apiKey, vault.toB64(pubkeySpki), vault.toB64(wrappedPrivkey), vault.ALG_MASTER_AES256GCM_V1)
    const cryptoKey = await vault.importSharingPrivkey(privkeyPkcs8)
    vaultSession.setSharingPrivkey(cryptoKey)
  } catch { /* best-effort; will retry on next unlock */ }
}

async function registerDevice(apiKey, masterKey, spki) {
  const wrapped = await vault.wrapMasterKeyForDevice(masterKey, spki)
  const body = {
    deviceId: deviceKeyManager.getDeviceId(),
    deviceLabel: deviceKeyManager.getDeviceLabel(),
    deviceKind: 'web',
    pubkeyFormat: 'p256-spki',
    pubkey: vault.toB64(spki),
    wrappedMasterKey: vault.toB64(wrapped),
    wrapFormat: vault.ALG_P256_ECDH_HKDF_V1,
  }
  const r = await fetch(`${API_URL}/api/keys/devices`, {
    method: 'POST',
    headers: { 'X-Api-Key': apiKey, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!r.ok) throw new Error(`Device registration failed: ${r.status}`)
}

// Cases:
// A — returning browser (isVaultSetUp=true, passphrase backup exists): unlock only
// B — new browser, existing account (isVaultSetUp=false, backup exists): unlock + register device
// C — brand new account (backup 404): generate master key + setup + register device

export function VaultUnlockPage({ apiKey, onUnlocked }) {
  // 'checking' | 'unlock' | 'setup' | 'working' | 'error'
  const [state, setState] = useState('checking')
  const [passphraseData, setPassphraseData] = useState(null)
  const [isNewBrowser, setIsNewBrowser] = useState(false)
  const [passphrase, setPassphrase] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const inputRef = useRef(null)

  useEffect(() => {
    async function probe() {
      try {
        const r = await fetch(`${API_URL}/api/keys/passphrase`, {
          headers: { 'X-Api-Key': apiKey },
        })
        if (r.status === 404) {
          setState('setup')
        } else if (r.ok) {
          setPassphraseData(await r.json())
          if (!deviceKeyManager.isVaultSetUp()) setIsNewBrowser(true)
          setState('unlock')
        } else {
          setErrorMsg(`Server error (${r.status}).`)
          setState('error')
        }
      } catch {
        setErrorMsg('Could not reach the server.')
        setState('error')
      }
    }
    probe()
  }, [apiKey])

  useEffect(() => {
    if (state === 'unlock' || state === 'setup') inputRef.current?.focus()
  }, [state])

  async function handleUnlock(e) {
    e.preventDefault()
    if (!passphrase) return
    setState('working')
    setErrorMsg('')
    try {
      const wrappedMasterKey = vault.fromB64(passphraseData.wrappedMasterKey)
      const salt = vault.fromB64(passphraseData.salt)
      const params = passphraseData.argon2Params ?? vault.DEFAULT_ARGON2_PARAMS
      const masterKey = await vault.unwrapMasterKeyWithPassphrase(wrappedMasterKey, passphrase, salt, params)

      if (isNewBrowser) {
        const spki = await deviceKeyManager.generateAndStoreKeypair()
        await registerDevice(apiKey, masterKey, spki)
        deviceKeyManager.markVaultSetUp()
      }

      vaultSession.unlock(masterKey)
      await loadSharingKey(apiKey, masterKey)
      await ensureSharingKey(apiKey, masterKey)
      onUnlocked()
    } catch (err) {
      setErrorMsg(err.message?.includes('passphrase') ? 'Incorrect passphrase.' : 'Incorrect passphrase.')
      setPassphrase('')
      setState('unlock')
    }
  }

  async function handleSetup(e) {
    e.preventDefault()
    if (!passphrase || passphrase !== confirm) return
    setState('working')
    setErrorMsg('')
    try {
      const masterKey = vault.generateMasterKey()

      const { envelope, salt, params } = await vault.wrapMasterKeyWithPassphrase(masterKey, passphrase)
      const putBody = {
        wrappedMasterKey: vault.toB64(envelope),
        wrapFormat: vault.ALG_ARGON2ID_AES256GCM_V1,
        argon2Params: params,
        salt: vault.toB64(salt),
      }
      const putR = await fetch(`${API_URL}/api/keys/passphrase`, {
        method: 'PUT',
        headers: { 'X-Api-Key': apiKey, 'Content-Type': 'application/json' },
        body: JSON.stringify(putBody),
      })
      if (!putR.ok) throw new Error(`Passphrase upload failed: ${putR.status}`)

      const spki = await deviceKeyManager.generateAndStoreKeypair()
      await registerDevice(apiKey, masterKey, spki)
      deviceKeyManager.markVaultSetUp()

      vaultSession.unlock(masterKey)
      await ensureSharingKey(apiKey, masterKey)
      onUnlocked()
    } catch {
      setErrorMsg('Something went wrong. Please try again.')
      setState('setup')
    }
  }

  if (state === 'checking' || state === 'working') {
    return (
      <div className="min-h-screen bg-parchment flex items-center justify-center">
        <WorkingDots size="lg" label="Unlocking…" />
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div className="min-h-screen bg-parchment flex items-center justify-center">
        <div className="bg-white rounded-card shadow-sm border border-forest-08 p-8 w-full max-w-sm text-center space-y-4">
          <p className="font-serif italic text-earth">{errorMsg}</p>
          <button onClick={() => window.location.reload()}
            className="text-sm text-forest underline">Try again</button>
        </div>
      </div>
    )
  }

  const isSetup = state === 'setup'
  const mismatch = isSetup && confirm && passphrase !== confirm
  const canSubmit = passphrase && (!isSetup || (confirm && !mismatch))

  return (
    <div className="min-h-screen bg-parchment flex items-center justify-center">
      <div className="bg-white rounded-card shadow-sm border border-forest-08 p-8 w-full max-w-sm">
        <div className="flex items-center gap-2 mb-1">
          <OliveBranchIcon width={20} />
          <span className="font-serif italic text-[17px] text-forest">Heirlooms</span>
        </div>
        <p className="text-sm text-text-muted mb-6">
          {isSetup
            ? 'Create a passphrase to protect your vault.'
            : 'Enter your passphrase to unlock your vault.'}
        </p>
        <form onSubmit={isSetup ? handleSetup : handleUnlock} className="space-y-4">
          <div className="space-y-1">
            <div className="relative">
              <input
                ref={inputRef}
                type={showPw ? 'text' : 'password'}
                value={passphrase}
                onChange={(e) => setPassphrase(e.target.value)}
                placeholder="Passphrase"
                autoComplete={isSetup ? 'new-password' : 'current-password'}
                className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25 pr-12"
              />
              <button type="button" onClick={() => setShowPw(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-muted hover:text-forest text-xs">
                {showPw ? 'Hide' : 'Show'}
              </button>
            </div>
            <p className="font-serif italic text-text-muted text-xs">
              {isSetup
                ? 'Your passphrase unlocks your vault from any browser. Keep it somewhere safe.'
                : 'Your passphrase protects your vault.'}
            </p>
          </div>

          {isSetup && (
            <div>
              <input
                type={showPw ? 'text' : 'password'}
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="Confirm passphrase"
                autoComplete="new-password"
                className={`w-full px-3 py-2 border rounded-button text-sm focus:outline-none focus:ring-2 ${
                  mismatch
                    ? 'border-earth focus:ring-earth/25'
                    : 'border-forest-15 focus:ring-forest-25'
                }`}
              />
              {mismatch && <p className="text-xs text-earth mt-1">Passphrases don't match.</p>}
            </div>
          )}

          {errorMsg && <p className="text-sm text-earth font-serif italic">{errorMsg}</p>}

          <button
            type="submit"
            disabled={!canSubmit}
            className="w-full bg-forest text-parchment py-2 rounded-button text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity"
          >
            {isSetup ? 'Save passphrase' : 'Unlock vault'}
          </button>
        </form>
        <p className="text-[10px] text-text-muted/40 text-right mt-4">{import.meta.env.VITE_COMMIT ?? 'dev'}</p>
      </div>
    </div>
  )
}
