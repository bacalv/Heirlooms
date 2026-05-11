import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { argon2id } from '@noble/hashes/argon2'
import { OliveBranchIcon } from '../brand/OliveBranchIcon'
import { authChallenge, authLogin, API_URL } from '../api'
import { fromB64url, toB64url, sha256, unwrapMasterKeyForDevice } from '../crypto/vaultCrypto'
import { unlock } from '../crypto/vaultSession'
import { loadPrivateKey, getDeviceId } from '../crypto/deviceKeyManager'

const ARGON2_PARAMS = { m: 65536, t: 3, p: 1 }

async function tryUnlockVaultAfterLogin(sessionToken, masterKeySeed) {
  try {
    const deviceId = getDeviceId()
    if (!deviceId) return null
    const privateKey = await loadPrivateKey()
    if (!privateKey) return null
    const r = await fetch(`${API_URL}/api/keys/devices`, { headers: { 'X-Api-Key': sessionToken } })
    if (!r.ok) return null
    const devices = await r.json()
    const thisDevice = devices.find(d => d.deviceId === deviceId)
    if (!thisDevice?.wrappedMasterKey) return null
    const envelope = Uint8Array.from(atob(thisDevice.wrappedMasterKey), c => c.charCodeAt(0))
    return unwrapMasterKeyForDevice(envelope, privateKey)
  } catch {
    return null
  }
}

export function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('')
  const [passphrase, setPassphrase] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState(null)
  const [working, setWorking] = useState(false)
  const usernameRef = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()
  const from = location.state?.from ?? '/'

  useEffect(() => { usernameRef.current?.focus() }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    const u = username.trim()
    const p = passphrase
    if (!u || !p) return
    setWorking(true)
    setError(null)
    try {
      const { auth_salt } = await authChallenge(u)
      const salt = fromB64url(auth_salt)
      const pw = new TextEncoder().encode(p)
      await new Promise(r => setTimeout(r, 0))
      const output = argon2id(pw, salt, { ...ARGON2_PARAMS, dkLen: 64 })
      const authKey = output.slice(0, 32)
      const masterKeySeed = output.slice(32, 64)
      const authKeyB64url = toB64url(authKey)
      const r = await authLogin(u, authKeyB64url)
      if (r.status === 401) { setError('Invalid username or passphrase.'); return }
      if (!r.ok) { setError(`Server error (${r.status}).`); return }
      const { session_token } = await r.json()
      const masterKey = await tryUnlockVaultAfterLogin(session_token, masterKeySeed)
      if (masterKey) unlock(masterKey)
      onLogin(session_token, masterKey)
      navigate(from, { replace: true })
    } catch {
      setError('Could not reach the server.')
    } finally {
      setWorking(false)
    }
  }

  return (
    <div className="min-h-screen bg-parchment flex items-center justify-center">
      <div className="bg-white rounded-card shadow-sm border border-forest-08 p-8 w-full max-w-sm">
        <div className="flex items-center gap-2 mb-1">
          <OliveBranchIcon width={20} />
          <span className="font-serif italic text-[17px] text-forest">Heirlooms</span>
        </div>
        <p className="text-sm text-text-muted mb-6">Sign in to continue.</p>
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            ref={usernameRef}
            type="text"
            value={username}
            onChange={e => setUsername(e.target.value)}
            placeholder="Username"
            autoComplete="username"
            className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25"
          />
          <div className="relative">
            <input
              type={showPw ? 'text' : 'password'}
              value={passphrase}
              onChange={e => setPassphrase(e.target.value)}
              placeholder="Passphrase"
              autoComplete="current-password"
              className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25 pr-12"
            />
            <button type="button" onClick={() => setShowPw(v => !v)}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-muted hover:text-forest text-xs">
              {showPw ? 'Hide' : 'Show'}
            </button>
          </div>
          {error && <p className="text-sm text-earth font-serif italic">{error}</p>}
          <button
            type="submit"
            disabled={working || !username.trim() || !passphrase}
            className="w-full bg-forest text-parchment py-2 rounded-button text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity"
          >
            {working ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="text-xs text-text-muted mt-4 text-center">
          Have an invite?{' '}
          <Link to="/join" className="text-forest underline">Create account</Link>
        </p>
        <p className="text-[10px] text-text-muted/40 text-right mt-2">{import.meta.env.VITE_COMMIT ?? 'dev'}</p>
      </div>
    </div>
  )
}
