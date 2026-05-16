import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { getAccount, getInvite } from '../api'

const INVITE_BASE = typeof window !== 'undefined' ? window.location.origin : 'https://heirlooms.digital'

function timeUntil(iso) {
  const ms = new Date(iso) - Date.now()
  const h = Math.floor(ms / 3_600_000)
  const m = Math.floor((ms % 3_600_000) / 60_000)
  if (h >= 1) return `${h} hour${h !== 1 ? 's' : ''}`
  if (m >= 1) return `${m} minute${m !== 1 ? 's' : ''}`
  return 'less than a minute'
}

export function AccessPage() {
  const { sessionToken } = useAuth()
  const [invite, setInvite] = useState(null)
  const [inviteError, setInviteError] = useState(null)
  const [inviteWorking, setInviteWorking] = useState(false)
  const [copied, setCopied] = useState(false)
  // SEC-015: biometric info note.
  const [requireBiometric, setRequireBiometric] = useState(false)

  useEffect(() => {
    if (!sessionToken) return
    getAccount(sessionToken)
      .then(data => setRequireBiometric(data.require_biometric ?? false))
      .catch(() => {})
  }, [sessionToken])

  async function handleGenerateInvite() {
    setInviteWorking(true)
    setInviteError(null)
    try {
      const data = await getInvite(sessionToken)
      setInvite(data)
    } catch {
      setInviteError('Could not generate invite. Please try again.')
    } finally {
      setInviteWorking(false)
    }
  }

  async function handleCopy(url) {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard blocked */
    }
  }

  const inviteUrl = invite ? `${INVITE_BASE}/join?token=${invite.token}` : null

  return (
    <div className="max-w-lg mx-auto px-4 py-8 space-y-10">
      <h1 className="font-serif text-2xl text-forest">Devices &amp; Access</h1>

      {/* SEC-015: biometric info note */}
      {requireBiometric && (
        <section className="flex items-start gap-3 px-4 py-3 bg-forest-08 border border-forest-15 rounded-lg">
          <span className="text-lg leading-none mt-0.5">&#x1F512;</span>
          <p className="text-sm text-forest">
            <strong>Biometric protection is active</strong> — your vault requires biometric authentication
            on your mobile devices. This setting can only be changed from the Heirlooms mobile app.
          </p>
        </section>
      )}

      {/* Invite someone */}
      <section className="space-y-3">
        <h2 className="text-base font-medium text-forest">Invite someone</h2>
        <p className="text-sm text-text-muted">
          Generate a link for a new user to create their account.
        </p>
        <button
          onClick={handleGenerateInvite}
          disabled={inviteWorking}
          className="px-4 py-2 bg-forest text-parchment rounded-button text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity"
        >
          {inviteWorking ? 'Generating…' : 'Generate invite link'}
        </button>
        {inviteError && <p className="text-sm text-earth">{inviteError}</p>}
        {inviteUrl && (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <input
                readOnly
                value={inviteUrl}
                className="flex-1 px-3 py-2 border border-forest-15 rounded-button text-sm bg-parchment/50 text-forest font-mono truncate"
              />
              <button
                onClick={() => handleCopy(inviteUrl)}
                className="px-3 py-2 border border-forest-15 rounded-button text-sm hover:bg-forest-08 transition-colors whitespace-nowrap"
              >
                {copied ? 'Copied!' : 'Copy'}
              </button>
            </div>
            <p className="text-xs text-text-muted">
              Expires in {timeUntil(invite.expires_at)}
            </p>
          </div>
        )}
      </section>

      {/* Link this browser */}
      <section className="space-y-3">
        <h2 className="text-base font-medium text-forest">Link this browser</h2>
        <p className="text-sm text-text-muted">
          Use a pairing code from the Heirlooms app on your phone to link this browser.
        </p>
        <Link
          to="/access/pair"
          className="inline-block px-4 py-2 border border-forest-25 text-forest rounded-button text-sm font-medium hover:bg-forest-08 transition-colors"
        >
          Pair with phone
        </Link>
      </section>
    </div>
  )
}
