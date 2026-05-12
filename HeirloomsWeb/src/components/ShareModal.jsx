import { useEffect, useState } from 'react'
import { getFriends, getFriendSharingKey, shareUpload } from '../api'
import { getMasterKey } from '../crypto/vaultSession'
import { unwrapDekWithMasterKey, fromB64, toB64, ALG_P256_ECDH_HKDF_V1, wrapDekForFriend } from '../crypto/vaultCrypto'

export function ShareModal({ upload, apiKey, onSuccess, onCancel }) {
  const [friends, setFriends] = useState(null)
  const [error, setError] = useState(null)
  const [sharing, setSharing] = useState(null) // friend id currently being shared to

  useEffect(() => {
    getFriends(apiKey)
      .then((data) => setFriends(data.friends ?? []))
      .catch(() => setError('Could not load friends.'))
  }, [apiKey])

  async function handleShare(friend) {
    setSharing(friend.id)
    setError(null)
    try {
      const { pubkey: pubkeyB64 } = await getFriendSharingKey(apiKey, friend.id)
      const friendPubkeyBytes = fromB64(pubkeyB64)

      const masterKey = getMasterKey()

      const rawDek = await unwrapDekWithMasterKey(fromB64(upload.wrappedDek), masterKey)
      const wrappedDekBytes = await wrapDekForFriend(rawDek, friendPubkeyBytes)

      let wrappedThumbnailDekB64 = null
      if (upload.wrappedThumbnailDek) {
        const rawThumbDek = await unwrapDekWithMasterKey(fromB64(upload.wrappedThumbnailDek), masterKey)
        wrappedThumbnailDekB64 = toB64(await wrapDekForFriend(rawThumbDek, friendPubkeyBytes))
      }

      await shareUpload(apiKey, upload.id, {
        toUserId: friend.id,
        wrappedDekB64: toB64(wrappedDekBytes),
        wrappedThumbnailDekB64,
        dekFormat: ALG_P256_ECDH_HKDF_V1,
        rotation: upload.rotation ?? 0,
      })
      onSuccess(`Shared with ${friend.displayName}`)
    } catch (e) {
      if (e.message === 'already_shared') {
        setError(`Already shared with ${friend.displayName}.`)
      } else {
        setError('Sharing failed. Try again.')
      }
      setSharing(null)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onCancel}>
      <div className="bg-white rounded-card shadow-xl w-full max-w-sm p-6 space-y-4" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-base font-medium text-forest">Share with a friend</h2>

        {!friends && !error && (
          <p className="text-sm text-text-muted">Loading…</p>
        )}

        {error && (
          <p className="text-sm text-earth">{error}</p>
        )}

        {friends && friends.length === 0 && (
          <p className="text-sm text-text-muted">You have no friends to share with yet.</p>
        )}

        {friends && friends.length > 0 && (
          <ul className="space-y-1">
            {friends.map((friend) => (
              <li key={friend.id}>
                <button
                  onClick={() => handleShare(friend)}
                  disabled={!!sharing}
                  className="w-full text-left px-3 py-2 rounded hover:bg-forest-04 transition-colors disabled:opacity-50 flex items-center justify-between"
                >
                  <div>
                    <span className="text-sm text-forest">{friend.displayName}</span>
                    <span className="text-xs text-text-muted ml-1.5">@{friend.username}</span>
                  </div>
                  {sharing === friend.id && (
                    <span className="text-xs text-text-muted">Sharing…</span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="pt-2 border-t border-forest-08">
          <button onClick={onCancel} disabled={!!sharing}
            className="text-sm text-text-muted hover:text-forest transition-colors disabled:opacity-40">
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
