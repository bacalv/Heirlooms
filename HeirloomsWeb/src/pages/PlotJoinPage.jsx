import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { apiFetch } from '../api'
import { WorkingDots } from '../brand/WorkingDots'
import { getSharingPrivkey } from '../crypto/vaultSession'
import { toB64 } from '../crypto/vaultCrypto'

// Fetches own sharing pubkey and redeems the invite token with it.
// Requires the inviter to confirm (wrap the plot key) before this user is fully added.

export function PlotJoinPage() {
  const { apiKey } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [info, setInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [joining, setJoining] = useState(false)
  const [result, setResult] = useState(null) // { inviterDisplayName }
  const [error, setError] = useState(null)

  useEffect(() => {
    document.title = 'Join plot · Heirlooms'
    if (!token) { setError('Missing invite token.'); setLoading(false); return }
    apiFetch(`/api/plots/join-info?token=${encodeURIComponent(token)}`, apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error('Invite not found or expired')))
      .then(setInfo)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [token, apiKey])

  async function handleJoin() {
    setJoining(true); setError(null)
    try {
      // Get own sharing pubkey so inviter can wrap plot key for us
      const spkResp = await apiFetch('/api/keys/sharing/me', apiKey)
      if (!spkResp.ok) throw new Error('Could not fetch own sharing pubkey')
      const { pubkey } = await spkResp.json()

      const r = await apiFetch('/api/plots/join', apiKey, {
        method: 'POST',
        body: JSON.stringify({ token, recipientSharingPubkey: pubkey }),
      })
      if (r.status === 409) { setError("You're already a member of this plot."); return }
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const data = await r.json()
      setResult({ inviterDisplayName: data.inviterDisplayName })
    } catch (e) {
      setError(e.message)
    } finally {
      setJoining(false)
    }
  }

  if (loading) return (
    <main className="max-w-md mx-auto px-4 py-16 flex justify-center">
      <WorkingDots size="lg" label="Loading invite…" />
    </main>
  )

  if (result) return (
    <main className="max-w-md mx-auto px-4 py-16">
      <div className="text-center space-y-4">
        <p className="font-serif italic text-forest text-xl">Request sent</p>
        <p className="text-sm text-text-muted font-sans">
          Your join request has been sent to <span className="font-medium text-forest">{result.inviterDisplayName}</span>.
          Once they confirm, the plot will appear in your Garden.
        </p>
        <button onClick={() => navigate('/')}
          className="px-4 py-2 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity">
          Back to Garden
        </button>
      </div>
    </main>
  )

  return (
    <main className="max-w-md mx-auto px-4 py-16">
      {error && !info ? (
        <div className="text-center space-y-4">
          <p className="text-earth font-sans text-sm">{error}</p>
          <button onClick={() => navigate('/')}
            className="px-4 py-2 border border-forest-25 text-forest rounded-button text-sm hover:bg-forest-08 transition-colors">
            Back to Garden
          </button>
        </div>
      ) : info ? (
        <div className="space-y-6">
          <div>
            <p className="font-serif italic text-forest text-xl mb-1">Join shared plot</p>
            <p className="text-text-muted text-sm font-sans">
              <span className="font-medium text-forest">{info.inviterDisplayName}</span> invited you to join:
            </p>
          </div>
          <div className="px-4 py-3 bg-forest-08 border border-forest-25 rounded-card">
            <p className="font-sans font-medium text-forest text-lg">{info.plotName}</p>
          </div>
          <p className="text-xs text-text-muted font-sans">
            Items in this plot are end-to-end encrypted. After you request to join,
            {info.inviterDisplayName} will wrap the plot key for you.
          </p>
          {error && <p className="text-earth text-xs font-sans">{error}</p>}
          <div className="flex gap-3">
            <button onClick={() => navigate('/')}
              className="px-4 py-2 border border-forest-15 text-text-muted rounded-button text-sm hover:text-forest transition-colors">
              Decline
            </button>
            <button onClick={handleJoin} disabled={joining}
              className="flex-1 px-4 py-2 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
              {joining ? '…' : 'Request to join'}
            </button>
          </div>
        </div>
      ) : null}
    </main>
  )
}
