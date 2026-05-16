import { useEffect, useState } from 'react'
import { useAuth } from '../AuthContext'
import { getFriends, createInvite, buildInviteUrl } from '../api'
import { WorkingDots } from '../brand/WorkingDots'

// ---- Person icon ------------------------------------------------------------

function PersonIcon() {
  return (
    <svg className="w-5 h-5 text-forest/40 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  )
}

// ---- Friend row -------------------------------------------------------------

function FriendRow({ friend }) {
  return (
    <div className="border border-forest-15 rounded-card px-4 py-3 bg-white flex items-center gap-3">
      <PersonIcon />
      <div className="flex-1 min-w-0">
        <p className="font-sans font-medium text-forest text-sm truncate">
          {friend.displayName || friend.username}
        </p>
        <p className="text-xs text-text-muted font-sans">@{friend.username}</p>
      </div>
    </div>
  )
}

// ---- Invite section ---------------------------------------------------------

function InviteSection({ apiKey }) {
  const [inviteUrl, setInviteUrl] = useState(null)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState(null)
  const [copied, setCopied] = useState(false)

  async function handleGenerate() {
    setGenerating(true)
    setError(null)
    setCopied(false)
    try {
      const { token } = await createInvite(apiKey)
      setInviteUrl(buildInviteUrl(token))
    } catch (e) {
      setError('Could not generate an invite link. Please try again.')
    } finally {
      setGenerating(false)
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(inviteUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2500)
    } catch {
      // Fallback: select the text for manual copy
    }
  }

  return (
    <div className="border border-forest-15 rounded-card p-4 bg-white mb-6">
      <div className="flex items-center gap-2 mb-1">
        <svg className="w-4 h-4 text-forest/60 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
            d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
        </svg>
        <h2 className="font-sans font-medium text-forest text-sm">Invite a friend</h2>
      </div>
      <p className="text-xs text-text-muted font-sans mb-3">
        Generate a single-use invite link. It expires once redeemed.
      </p>

      {error && (
        <p className="text-xs text-earth font-serif italic mb-3">{error}</p>
      )}

      {!inviteUrl ? (
        <button
          onClick={handleGenerate}
          disabled={generating}
          className="bg-forest text-parchment text-sm font-medium px-4 py-2 rounded-button hover:opacity-90 disabled:opacity-40 transition-opacity"
        >
          {generating ? 'Generating…' : 'Generate invite link'}
        </button>
      ) : (
        <div className="space-y-2">
          <div className="flex items-center gap-2 bg-parchment border border-forest-15 rounded-button px-3 py-2">
            <span className="font-sans text-xs text-forest truncate flex-1 select-all">{inviteUrl}</span>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleCopy}
              className="flex-1 bg-forest text-parchment text-sm font-medium px-4 py-2 rounded-button hover:opacity-90 transition-opacity"
            >
              {copied ? 'Copied!' : 'Copy link'}
            </button>
            <button
              onClick={handleGenerate}
              disabled={generating}
              className="text-xs text-text-muted font-sans px-3 py-2 border border-forest-15 rounded-button hover:text-forest disabled:opacity-40 transition-colors"
            >
              New link
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ---- Main page --------------------------------------------------------------

export function FriendsPage() {
  const { apiKey } = useAuth()
  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    document.title = 'Friends · Heirlooms'
    getFriends(apiKey)
      .then(({ friends }) => setFriends(friends))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [apiKey])

  return (
    <main className="max-w-2xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h1 className="font-serif italic text-forest text-2xl">Friends</h1>
        <p className="text-sm text-text-muted font-sans mt-1">
          People you've connected with on Heirlooms.
        </p>
      </div>

      <InviteSection apiKey={apiKey} />

      {loading && (
        <div className="flex justify-center py-16">
          <WorkingDots size="lg" />
        </div>
      )}

      {!loading && error && (
        <p className="text-center text-earth font-serif italic py-16">{error}</p>
      )}

      {!loading && !error && friends.length === 0 && (
        <div className="text-center py-16">
          <div className="flex justify-center mb-4">
            <svg className="w-10 h-10 text-forest/20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1}
                d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          </div>
          <p className="font-serif italic text-text-muted">No friends yet.</p>
          <p className="text-sm text-text-muted font-sans mt-2">
            Friends are added automatically when someone redeems your invite link.
          </p>
        </div>
      )}

      {!loading && !error && friends.length > 0 && (
        <div className="space-y-3">
          {friends.map((friend) => (
            <FriendRow key={friend.id} friend={friend} />
          ))}
        </div>
      )}
    </main>
  )
}
