import { useEffect, useState } from 'react'
import { apiFetch } from '../api'
import { BrandModal } from './BrandModal'
import { wrapPlotKeyForMember, unwrapPlotKey, fromB64, toB64 } from '../crypto/vaultCrypto'
import { getSharingPrivkey } from '../crypto/vaultSession'

// Invite a friend directly (they're already in your friends list).
// The inviter wraps the plot key for the friend and posts it immediately.
function FriendsTab({ plotId, apiKey, onDone }) {
  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState(null)
  const [done, setDone] = useState(false)

  useEffect(() => {
    apiFetch('/api/friends', apiKey)
      .then((r) => r.ok ? r.json() : [])
      .then((arr) => setFriends(Array.isArray(arr) ? arr.map((f) => ({
        id: f.userId, username: f.username, displayName: f.displayName
      })) : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [apiKey])

  async function handleInvite() {
    if (!selected) return
    setWorking(true); setError(null)
    try {
      // 1. Get own wrapped plot key + unwrap it
      const pkResp = await apiFetch(`/api/plots/${plotId}/plot-key`, apiKey)
      if (!pkResp.ok) throw new Error('Could not fetch plot key')
      const { wrappedPlotKey, plotKeyFormat } = await pkResp.json()
      const sharingPrivkey = getSharingPrivkey()
      if (!sharingPrivkey) throw new Error('Sharing key not loaded — try logging in again')
      const plotKeyBytes = await unwrapPlotKey(fromB64(wrappedPlotKey), sharingPrivkey)

      // 2. Get friend's sharing pubkey
      const spkResp = await apiFetch(`/api/keys/sharing/${encodeURIComponent(selected.id)}`, apiKey)
      if (!spkResp.ok) throw new Error("Could not fetch friend's sharing key")
      const { pubkey: friendPubkeyB64 } = await spkResp.json()
      const friendPubkey = fromB64(friendPubkeyB64)

      // 3. Wrap plot key for friend
      const { wrappedKey, format } = await wrapPlotKeyForMember(plotKeyBytes, friendPubkey)

      // 4. Post to members endpoint
      const addResp = await apiFetch(`/api/plots/${plotId}/members`, apiKey, {
        method: 'POST',
        body: JSON.stringify({
          userId: selected.id,
          wrappedPlotKey: toB64(wrappedKey),
          plotKeyFormat: format,
        }),
      })
      if (!addResp.ok) {
        const msg = await addResp.text()
        throw new Error(msg || `HTTP ${addResp.status}`)
      }
      setDone(true)
      onDone?.()
    } catch (e) {
      setError(e.message)
    } finally {
      setWorking(false)
    }
  }

  if (done) return (
    <p className="text-sm font-sans text-forest text-center py-4">
      {selected?.displayName} has been added to the plot.
    </p>
  )

  if (loading) return <p className="text-text-muted text-sm text-center py-4">Loading friends…</p>

  return (
    <div className="space-y-3">
      {friends.length === 0 ? (
        <p className="text-text-muted text-sm">No friends yet. Add friends from the Friends page.</p>
      ) : (
        <div className="space-y-1 max-h-48 overflow-y-auto">
          {friends.map((f) => (
            <button key={f.id} onClick={() => setSelected(f)}
              className={`w-full text-left px-3 py-2 rounded text-sm font-sans transition-colors ${
                selected?.id === f.id
                  ? 'bg-forest text-parchment'
                  : 'hover:bg-forest-08 text-forest'
              }`}>
              <span className="font-medium">{f.displayName}</span>
              <span className="text-xs ml-2 opacity-70">@{f.username}</span>
            </button>
          ))}
        </div>
      )}
      {error && <p className="text-earth text-xs">{error}</p>}
      <button onClick={handleInvite} disabled={!selected || working}
        className="w-full px-4 py-2 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
        {working ? 'Inviting…' : 'Invite to plot'}
      </button>
    </div>
  )
}

// Confirm pending joins from invite-link redemptions.
function PendingTab({ plotId, apiKey, onDone }) {
  const [pending, setPending] = useState([])
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState({}) // inviteId → true
  const [error, setError] = useState(null)

  function reload() {
    setLoading(true)
    apiFetch(`/api/plots/${plotId}/members/pending`, apiKey)
      .then((r) => r.ok ? r.json() : [])
      .then((arr) => setPending(Array.isArray(arr) ? arr : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => { reload() }, [plotId, apiKey])

  async function handleConfirm(invite) {
    setWorking((w) => ({ ...w, [invite.id]: true }))
    setError(null)
    try {
      const pkResp = await apiFetch(`/api/plots/${plotId}/plot-key`, apiKey)
      if (!pkResp.ok) throw new Error('Could not fetch plot key')
      const { wrappedPlotKey } = await pkResp.json()
      const sharingPrivkey = getSharingPrivkey()
      if (!sharingPrivkey) throw new Error('Sharing key not loaded — try logging in again')
      const plotKeyBytes = await unwrapPlotKey(fromB64(wrappedPlotKey), sharingPrivkey)

      const recipientPubkey = fromB64(invite.recipientPubkey)
      const { wrappedKey, format } = await wrapPlotKeyForMember(plotKeyBytes, recipientPubkey)

      const r = await apiFetch(`/api/plots/${plotId}/members/pending/${encodeURIComponent(invite.id)}/confirm`, apiKey, {
        method: 'POST',
        body: JSON.stringify({ wrappedPlotKey: toB64(wrappedKey), plotKeyFormat: format }),
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      onDone?.()
      reload()
    } catch (e) {
      setError(e.message)
    } finally {
      setWorking((w) => { const n = { ...w }; delete n[invite.id]; return n })
    }
  }

  if (loading) return <p className="text-text-muted text-sm text-center py-4">Loading…</p>

  return (
    <div className="space-y-3">
      {pending.length === 0 ? (
        <p className="text-text-muted text-sm text-center py-4">
          No pending joins. Friends added directly via the Friends tab don't appear here — this tab is only for people who redeemed an invite link.
        </p>
      ) : (
        <div className="space-y-1 max-h-48 overflow-y-auto">
          {pending.map((inv) => (
            <div key={inv.id} className="flex items-center justify-between px-3 py-2 rounded hover:bg-forest-04">
              <span className="text-sm font-sans text-forest">
                <span className="font-medium">{inv.displayName}</span>
                <span className="text-xs ml-2 opacity-70">@{inv.username}</span>
              </span>
              <button onClick={() => handleConfirm(inv)} disabled={!!working[inv.id]}
                className="px-3 py-1 text-xs bg-forest text-parchment rounded-button hover:opacity-90 transition-opacity disabled:opacity-40">
                {working[inv.id] ? '…' : 'Confirm'}
              </button>
            </div>
          ))}
        </div>
      )}
      {error && <p className="text-earth text-xs">{error}</p>}
    </div>
  )
}

// Generate an invite link (token). The recipient redeems it, then the inviter confirms.
function InviteLinkTab({ plotId, apiKey }) {
  const [token, setToken] = useState(null)
  const [expiresAt, setExpiresAt] = useState(null)
  const [working, setWorking] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState(null)

  async function handleGenerate() {
    setWorking(true); setError(null)
    try {
      const r = await apiFetch(`/api/plots/${plotId}/invites`, apiKey, { method: 'POST', body: '{}' })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const data = await r.json()
      setToken(data.token)
      setExpiresAt(data.expiresAt)
    } catch (e) { setError(e.message) } finally { setWorking(false) }
  }

  const link = token ? `https://heirlooms.digital/plots/join?token=${token}` : null

  return (
    <div className="space-y-3">
      {!token ? (
        <button onClick={handleGenerate} disabled={working}
          className="w-full px-4 py-2 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
          {working ? '…' : 'Generate invite link'}
        </button>
      ) : (
        <>
          <div className="bg-forest-04 border border-forest-15 rounded p-3">
            <p className="text-xs text-text-muted font-mono break-all">{link}</p>
          </div>
          <button onClick={() => { navigator.clipboard.writeText(link); setCopied(true); setTimeout(() => setCopied(false), 2000) }}
            className="w-full px-4 py-2 border border-forest-25 text-forest rounded-button text-sm hover:bg-forest-08 transition-colors">
            {copied ? 'Copied!' : 'Copy link'}
          </button>
          <p className="text-xs text-text-muted font-sans">
            Expires in 48 hours. After the recipient opens the link, you'll see them in the
            <span className="font-medium text-forest"> Pending joins</span> section of this plot's member list.
            You'll need to confirm their join to wrap the plot key for them.
          </p>
        </>
      )}
      {error && <p className="text-earth text-xs">{error}</p>}
    </div>
  )
}

export function InviteMemberModal({ plotId, apiKey, onClose, onMemberAdded }) {
  const [tab, setTab] = useState('friends')

  return (
    <BrandModal onClose={onClose} width="max-w-sm">
      <div className="p-6">
      <h2 className="font-serif italic text-forest text-lg mb-4">Invite a member</h2>

      <div className="flex border-b border-forest-08 mb-4 -mx-6 px-6">
        {[['friends', 'Friends'], ['link', 'Invite link'], ['pending', 'Pending']].map(([key, label]) => (
          <button key={key} onClick={() => setTab(key)}
            className={`px-4 py-2 text-sm font-sans transition-colors ${
              tab === key
                ? 'border-b-2 border-forest text-forest'
                : 'text-text-muted hover:text-forest'
            }`}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'friends' && (
        <FriendsTab plotId={plotId} apiKey={apiKey} onDone={() => { onMemberAdded?.(); onClose() }} />
      )}
      {tab === 'link' && <InviteLinkTab plotId={plotId} apiKey={apiKey} />}
      {tab === 'pending' && (
        <PendingTab plotId={plotId} apiKey={apiKey} onDone={() => onMemberAdded?.()} />
      )}
      </div>
    </BrandModal>
  )
}
