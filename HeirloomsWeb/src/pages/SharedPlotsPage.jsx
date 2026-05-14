import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../AuthContext'
import { WorkingDots } from '../brand/WorkingDots'
import { BrandModal } from '../components/BrandModal'
import { useDragDrop } from '../components/DragDropProvider'
import {
  listSharedMemberships, listPlotMembers,
  acceptPlotInvite, leaveSharedPlot, rejoinSharedPlot,
  restoreSharedPlot, transferPlotOwnership, setSharedPlotStatus,
} from '../api'

// ---- Name prompt modal (accept / rejoin) ------------------------------------

function NamePromptModal({ title, subtitle, initialName = '', onConfirm, onClose }) {
  const [name, setName] = useState(initialName)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState(null)
  const inputRef = useRef(null)

  useEffect(() => { inputRef.current?.focus() }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) return
    setWorking(true); setError(null)
    try { await onConfirm(name.trim()) }
    catch (err) { setError(err.message); setWorking(false) }
  }

  return (
    <BrandModal onClose={onClose} width="max-w-xs">
      <div className="p-6 space-y-4">
        <div>
          <p className="font-serif italic text-forest text-lg">{title}</p>
          {subtitle && <p className="text-sm text-text-muted font-sans mt-1">{subtitle}</p>}
        </div>
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            ref={inputRef}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Your name for this plot"
            maxLength={80}
            className="w-full text-sm border border-forest-15 rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent"
          />
          {error && <p className="text-xs text-earth">{error}</p>}
          <div className="flex gap-2 pt-1">
            <button type="submit" disabled={!name.trim() || working}
              className="flex-1 px-4 py-2 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
              {working ? '…' : 'Confirm'}
            </button>
            <button type="button" onClick={onClose} disabled={working}
              className="px-4 py-2 text-text-muted hover:text-forest transition-colors text-sm">
              Cancel
            </button>
          </div>
        </form>
      </div>
    </BrandModal>
  )
}

// ---- Transfer ownership modal -----------------------------------------------

function TransferOwnershipModal({ plotId, apiKey, onDone, onClose }) {
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    listPlotMembers(apiKey, plotId)
      .then((arr) => setMembers(arr.filter((m) => m.role !== 'owner' && m.status === 'joined')))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [apiKey, plotId])

  async function handleTransfer() {
    if (!selected) return
    setWorking(true); setError(null)
    try {
      await transferPlotOwnership(apiKey, plotId, selected.userId)
      onDone()
    } catch (e) {
      setError(e.message)
      setWorking(false)
    }
  }

  return (
    <BrandModal onClose={onClose} width="max-w-sm">
      <div className="p-6 space-y-4">
        <p className="font-serif italic text-forest text-lg">Transfer ownership</p>
        <p className="text-sm text-text-muted font-sans">
          Choose a member to become the new owner. You'll remain a member of the plot.
        </p>
        {loading ? (
          <div className="flex justify-center py-4"><WorkingDots size="md" /></div>
        ) : members.length === 0 ? (
          <p className="text-sm text-text-muted text-center py-4">No other joined members.</p>
        ) : (
          <div className="space-y-1 max-h-48 overflow-y-auto">
            {members.map((m) => (
              <button key={m.userId} onClick={() => setSelected(m)}
                className={`w-full text-left px-3 py-2 rounded text-sm font-sans transition-colors ${
                  selected?.userId === m.userId
                    ? 'bg-forest text-parchment'
                    : 'hover:bg-forest-08 text-forest'
                }`}>
                <span className="font-medium">{m.displayName}</span>
                <span className="text-xs ml-2 opacity-70">@{m.username}</span>
              </button>
            ))}
          </div>
        )}
        {error && <p className="text-xs text-earth">{error}</p>}
        <div className="flex gap-2 pt-1">
          <button onClick={handleTransfer} disabled={!selected || working || loading}
            className="flex-1 px-4 py-2 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
            {working ? '…' : 'Transfer'}
          </button>
          <button onClick={onClose} disabled={working}
            className="px-4 py-2 text-text-muted hover:text-forest transition-colors text-sm">
            Cancel
          </button>
        </div>
      </div>
    </BrandModal>
  )
}

// ---- Section header ---------------------------------------------------------

function SectionHeader({ title, count }) {
  if (count === 0) return null
  return (
    <div className="flex items-center gap-2 mb-3">
      <h2 className="font-serif italic text-forest text-base">{title}</h2>
      <span className="text-xs text-text-muted bg-forest-08 rounded-full px-2 py-0.5">{count}</span>
    </div>
  )
}

// ---- People icon ------------------------------------------------------------

function PeopleIcon() {
  return (
    <svg className="w-4 h-4 text-text-muted flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
    </svg>
  )
}

// ---- Shared plot card -------------------------------------------------------

function MembershipCard({ membership, apiKey, onAction }) {
  const isOwner = membership.role === 'owner'
  const isClosed = membership.plotStatus === 'closed'
  const displayName = membership.localName || membership.plotName

  const [leaving, setLeaving] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [leaveError, setLeaveError] = useState(null)

  async function handleLeave() {
    if (!window.confirm(`Leave "${displayName}"?`)) return
    setLeaving(true); setLeaveError(null)
    try {
      await leaveSharedPlot(apiKey, membership.plotId)
      onAction()
    } catch (e) {
      if (e.message === 'must_transfer') {
        setLeaveError('Transfer ownership to another member before leaving.')
      } else {
        setLeaveError(e.message)
      }
      setLeaving(false)
    }
  }

  async function handleToggleStatus() {
    const newStatus = isClosed ? 'open' : 'closed'
    setToggling(true)
    try {
      await setSharedPlotStatus(apiKey, membership.plotId, newStatus)
      onAction()
    } catch { /* ignore */ } finally { setToggling(false) }
  }

  return (
    <div className="border border-forest-15 rounded-card px-4 py-3 bg-white space-y-2">
      <div className="flex items-start gap-2">
        <PeopleIcon />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-sans font-medium text-forest text-sm truncate">{displayName}</p>
            {isClosed && (
              <span className="text-[10px] text-text-muted border border-forest-15 rounded px-1 flex-shrink-0">closed</span>
            )}
            {isOwner && (
              <span className="text-[10px] text-forest/60 border border-forest-15 rounded px-1 flex-shrink-0">owner</span>
            )}
          </div>
          {membership.ownerDisplayName && !isOwner && (
            <p className="text-xs text-text-muted font-sans">Shared by {membership.ownerDisplayName}</p>
          )}
        </div>
      </div>
      {leaveError && <p className="text-xs text-earth">{leaveError}</p>}
      <div className="flex flex-wrap gap-2 pt-1">
        {isOwner && (
          <button onClick={handleToggleStatus} disabled={toggling}
            className="px-3 py-1 text-xs border border-forest-25 text-forest rounded-button hover:bg-forest-08 transition-colors disabled:opacity-40">
            {toggling ? '…' : isClosed ? 'Reopen' : 'Close plot'}
          </button>
        )}
        {isOwner && (
          <button onClick={() => onAction('transfer', membership)}
            className="px-3 py-1 text-xs border border-forest-25 text-forest rounded-button hover:bg-forest-08 transition-colors">
            Transfer ownership
          </button>
        )}
        <button onClick={handleLeave} disabled={leaving}
          className="px-3 py-1 text-xs border border-earth/40 text-earth rounded-button hover:bg-earth/5 transition-colors disabled:opacity-40">
          {leaving ? '…' : 'Leave'}
        </button>
      </div>
    </div>
  )
}

// ---- Invitation card --------------------------------------------------------

function InvitationCard({ membership, apiKey, onAction }) {
  const [promptOpen, setPromptOpen] = useState(false)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState(null)

  async function handleAccept(localName) {
    setWorking(true); setError(null)
    try {
      await acceptPlotInvite(apiKey, membership.plotId, localName)
      setPromptOpen(false)
      onAction()
    } catch (e) {
      throw e
    } finally {
      setWorking(false)
    }
  }

  return (
    <>
      <div className="border border-forest-25 rounded-card px-4 py-3 bg-forest-04 space-y-2">
        <div className="flex items-start gap-2">
          <PeopleIcon />
          <div className="flex-1 min-w-0">
            <p className="font-sans font-medium text-forest text-sm">{membership.plotName}</p>
            {membership.ownerDisplayName && (
              <p className="text-xs text-text-muted font-sans">From {membership.ownerDisplayName}</p>
            )}
          </div>
        </div>
        {error && <p className="text-xs text-earth">{error}</p>}
        <button onClick={() => setPromptOpen(true)} disabled={working}
          className="px-4 py-1.5 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
          {working ? '…' : 'Accept'}
        </button>
      </div>
      {promptOpen && (
        <NamePromptModal
          title="Join shared plot"
          subtitle={`Choose a name for "${membership.plotName}" in your garden.`}
          onConfirm={handleAccept}
          onClose={() => setPromptOpen(false)}
        />
      )}
    </>
  )
}

// ---- Left card (re-join) ----------------------------------------------------

function LeftCard({ membership, apiKey, onAction }) {
  const [promptOpen, setPromptOpen] = useState(false)
  const [working, setWorking] = useState(false)

  async function handleRejoin(localName) {
    setWorking(true)
    try {
      await rejoinSharedPlot(apiKey, membership.plotId, localName || undefined)
      setPromptOpen(false)
      onAction()
    } catch (e) {
      throw e
    } finally {
      setWorking(false)
    }
  }

  const displayName = membership.localName || membership.plotName

  return (
    <>
      <div className="border border-forest-08 rounded-card px-4 py-3 space-y-2 opacity-70">
        <div className="flex items-start gap-2">
          <PeopleIcon />
          <div className="flex-1 min-w-0">
            <p className="font-sans text-forest text-sm">{displayName}</p>
            {membership.ownerDisplayName && (
              <p className="text-xs text-text-muted font-sans">Shared by {membership.ownerDisplayName}</p>
            )}
          </div>
        </div>
        <button onClick={() => setPromptOpen(true)} disabled={working}
          className="px-3 py-1.5 text-sm border border-forest-25 text-forest rounded-button hover:bg-forest-08 transition-colors disabled:opacity-40">
          {working ? '…' : 'Re-join'}
        </button>
      </div>
      {promptOpen && (
        <NamePromptModal
          title="Re-join plot"
          subtitle={membership.localName ? `Previous name: "${membership.localName}"` : undefined}
          initialName={membership.localName || ''}
          onConfirm={handleRejoin}
          onClose={() => setPromptOpen(false)}
        />
      )}
    </>
  )
}

// ---- Tombstoned card (restore) ----------------------------------------------

function TombstonedCard({ membership, apiKey, onAction }) {
  const [working, setWorking] = useState(false)
  const [error, setError] = useState(null)

  const displayName = membership.localName || membership.plotName

  async function handleRestore() {
    setWorking(true); setError(null)
    try {
      await restoreSharedPlot(apiKey, membership.plotId)
      onAction()
    } catch (e) {
      if (e.message === 'not_authorized') setError('Only the member who removed the plot can restore it.')
      else if (e.message === 'window_expired') setError('The restore window has expired.')
      else setError(e.message)
      setWorking(false)
    }
  }

  const removedAt = membership.tombstonedAt ? new Date(membership.tombstonedAt) : null
  const daysLeft = removedAt
    ? Math.max(0, Math.ceil((removedAt.getTime() + 90 * 24 * 60 * 60 * 1000 - Date.now()) / (24 * 60 * 60 * 1000)))
    : null

  return (
    <div className="border border-earth/20 rounded-card px-4 py-3 space-y-2 opacity-60">
      <div className="flex items-start gap-2">
        <PeopleIcon />
        <div className="flex-1 min-w-0">
          <p className="font-sans text-forest text-sm line-through">{displayName}</p>
          {daysLeft !== null && (
            <p className="text-xs text-text-muted font-sans">
              {daysLeft > 0 ? `${daysLeft} days to restore` : 'Expired'}
            </p>
          )}
        </div>
      </div>
      {error && <p className="text-xs text-earth">{error}</p>}
      {daysLeft !== null && daysLeft > 0 && (
        <button onClick={handleRestore} disabled={working}
          className="px-3 py-1.5 text-sm border border-forest-25 text-forest rounded-button hover:bg-forest-08 transition-colors disabled:opacity-40">
          {working ? '…' : 'Restore'}
        </button>
      )}
    </div>
  )
}

// ---- Main page --------------------------------------------------------------

export function SharedPlotsPage() {
  const { apiKey } = useAuth()
  const { triggerUpload } = useDragDrop() ?? {}
  const [memberships, setMemberships] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [transferTarget, setTransferTarget] = useState(null)

  function reload() {
    setLoading(true)
    listSharedMemberships(apiKey)
      .then(setMemberships)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    document.title = 'Shared plots · Heirlooms'
    reload()
  }, [apiKey]) // eslint-disable-line react-hooks/exhaustive-deps

  // Paste-to-upload: listen for image pastes on this page.
  useEffect(() => {
    if (!triggerUpload) return
    function handlePaste(e) {
      const items = Array.from(e.clipboardData?.items ?? [])
      const imageItem = items.find((i) => i.kind === 'file' && i.type.startsWith('image/'))
      if (!imageItem) return
      const file = imageItem.getAsFile()
      if (!file) return
      triggerUpload([file])
    }
    document.addEventListener('paste', handlePaste)
    return () => document.removeEventListener('paste', handlePaste)
  }, [triggerUpload])

  function handleAction(action, membership) {
    if (action === 'transfer') { setTransferTarget(membership); return }
    reload()
  }

  const invitations    = memberships.filter((m) => m.status === 'invited')
  const joined         = memberships.filter((m) => m.status === 'joined' && !m.tombstonedAt)
  const left           = memberships.filter((m) => m.status === 'left' && !m.tombstonedAt)
  const tombstoned     = memberships.filter((m) => m.tombstonedAt)
  const isEmpty = memberships.length === 0

  return (
    <main className="max-w-2xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h1 className="font-serif italic text-forest text-2xl">Shared plots</h1>
        <p className="text-sm text-text-muted font-sans mt-1">
          Plots you've been invited to, are a member of, or have left.
        </p>
      </div>

      {loading && (
        <div className="flex justify-center py-16"><WorkingDots size="lg" /></div>
      )}

      {!loading && error && (
        <p className="text-center text-earth font-serif italic py-16">{error}</p>
      )}

      {!loading && !error && isEmpty && (
        <div className="text-center py-16">
          <PeopleIcon />
          <p className="font-serif italic text-text-muted mt-4">No shared plots yet.</p>
          <p className="text-sm text-text-muted font-sans mt-2">
            Shared plots appear here when you're invited by someone or when you create one.
          </p>
        </div>
      )}

      {!loading && !error && !isEmpty && (
        <div className="space-y-8">
          {invitations.length > 0 && (
            <section>
              <SectionHeader title="Invitations" count={invitations.length} />
              <div className="space-y-3">
                {invitations.map((m) => (
                  <InvitationCard key={m.plotId} membership={m} apiKey={apiKey} onAction={reload} />
                ))}
              </div>
            </section>
          )}

          {joined.length > 0 && (
            <section>
              <SectionHeader title="Joined" count={joined.length} />
              <div className="space-y-3">
                {joined.map((m) => (
                  <MembershipCard key={m.plotId} membership={m} apiKey={apiKey} onAction={handleAction} />
                ))}
              </div>
            </section>
          )}

          {left.length > 0 && (
            <section>
              <SectionHeader title="Left" count={left.length} />
              <div className="space-y-3">
                {left.map((m) => (
                  <LeftCard key={m.plotId} membership={m} apiKey={apiKey} onAction={reload} />
                ))}
              </div>
            </section>
          )}

          {tombstoned.length > 0 && (
            <section>
              <SectionHeader title="Recently removed" count={tombstoned.length} />
              <div className="space-y-3">
                {tombstoned.map((m) => (
                  <TombstonedCard key={m.plotId} membership={m} apiKey={apiKey} onAction={reload} />
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {transferTarget && (
        <TransferOwnershipModal
          plotId={transferTarget.plotId}
          apiKey={apiKey}
          onDone={() => { setTransferTarget(null); reload() }}
          onClose={() => setTransferTarget(null)}
        />
      )}
    </main>
  )
}
