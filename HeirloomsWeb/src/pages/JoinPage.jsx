import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { argon2id } from '@noble/hashes/argon2'
import { OliveBranchIcon } from '../brand/OliveBranchIcon'
import { authRegister, API_URL } from '../api'
import {
  generateMasterKey, generateSalt, wrapMasterKeyForDevice,
  toB64, toB64url, sha256,
} from '../crypto/vaultCrypto'
import { unlock } from '../crypto/vaultSession'
import { generateAndStoreKeypair, getDeviceId, getDeviceLabel, markVaultSetUp } from '../crypto/deviceKeyManager'

const ARGON2_PARAMS = { m: 65536, t: 3, p: 1 }

export function JoinPage({ onJoined }) {
  const [searchParams] = useSearchParams()
  const [inviteToken, setInviteToken] = useState(searchParams.get('token') ?? '')
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [passphrase, setPassphrase] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState(null)
  const [working, setWorking] = useState(false)
  const firstRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => { firstRef.current?.focus() }, [])

  const mismatch = confirm && passphrase !== confirm
  const canSubmit = inviteToken && username.trim() && displayName.trim() && passphrase && confirm && !mismatch

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setWorking(true)
    setError(null)
    try {
      const authSalt = generateSalt(16)
      const pw = new TextEncoder().encode(passphrase)
      await new Promise(r => setTimeout(r, 0))
      const output = argon2id(pw, authSalt, { ...ARGON2_PARAMS, dkLen: 64 })
      const authKey = output.slice(0, 32)
      const authVerifier = await sha256(authKey)

      const masterKey = generateMasterKey()
      const spki = await generateAndStoreKeypair()

      if (!getDeviceId()) {
        localStorage.setItem('heirlooms-deviceId', crypto.randomUUID())
      }

      const wrappedMasterKey = await wrapMasterKeyForDevice(masterKey, spki)

      const body = {
        invite_token: inviteToken,
        username: username.trim(),
        display_name: displayName.trim(),
        auth_salt: toB64url(authSalt),
        auth_verifier: toB64url(authVerifier),
        wrapped_master_key: toB64(wrappedMasterKey),
        wrap_format: 'p256-ecdh-hkdf-aes256gcm-v1',
        pubkey_format: 'p256-spki',
        pubkey: toB64(spki),
        device_id: getDeviceId() ?? crypto.randomUUID(),
        device_label: getDeviceLabel(),
        device_kind: 'web',
      }

      const r = await authRegister(body)
      if (r.status === 409) { setError('Username already taken.'); return }
      if (r.status === 410) { setError('Invite link is invalid or expired.'); return }
      if (!r.ok) { setError(`Server error (${r.status}).`); return }

      const { session_token } = await r.json()
      markVaultSetUp()
      unlock(masterKey)
      onJoined(session_token, masterKey)
      navigate('/', { replace: true })
    } catch {
      setError('Something went wrong. Please try again.')
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
        <p className="text-sm text-text-muted mb-6">Create your account.</p>
        <form onSubmit={handleSubmit} className="space-y-3">
          {!searchParams.get('token') && (
            <input
              ref={firstRef}
              type="text"
              value={inviteToken}
              onChange={e => setInviteToken(e.target.value)}
              placeholder="Invite code"
              className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25"
            />
          )}
          <input
            ref={searchParams.get('token') ? firstRef : null}
            type="text"
            value={username}
            onChange={e => setUsername(e.target.value)}
            placeholder="Username"
            autoComplete="username"
            className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25"
          />
          <input
            type="text"
            value={displayName}
            onChange={e => setDisplayName(e.target.value)}
            placeholder="Display name"
            autoComplete="name"
            className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25"
          />
          <div className="relative">
            <input
              type={showPw ? 'text' : 'password'}
              value={passphrase}
              onChange={e => setPassphrase(e.target.value)}
              placeholder="Passphrase"
              autoComplete="new-password"
              className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25 pr-12"
            />
            <button type="button" onClick={() => setShowPw(v => !v)}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-muted hover:text-forest text-xs">
              {showPw ? 'Hide' : 'Show'}
            </button>
          </div>
          <input
            type={showPw ? 'text' : 'password'}
            value={confirm}
            onChange={e => setConfirm(e.target.value)}
            placeholder="Confirm passphrase"
            autoComplete="new-password"
            className={`w-full px-3 py-2 border rounded-button text-sm focus:outline-none focus:ring-2 ${
              mismatch ? 'border-earth focus:ring-earth/25' : 'border-forest-15 focus:ring-forest-25'
            }`}
          />
          {mismatch && <p className="text-xs text-earth">Passphrases don't match.</p>}
          {error && <p className="text-sm text-earth font-serif italic">{error}</p>}
          <button
            type="submit"
            disabled={working || !canSubmit}
            className="w-full bg-forest text-parchment py-2 rounded-button text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity"
          >
            {working ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="text-xs text-text-muted mt-4 text-center">
          Already have an account?{' '}
          <Link to="/login" className="text-forest underline">Sign in</Link>
        </p>
        <p className="text-[10px] text-text-muted/40 text-right mt-2">{import.meta.env.VITE_COMMIT ?? 'dev'}</p>
      </div>
    </div>
  )
}
