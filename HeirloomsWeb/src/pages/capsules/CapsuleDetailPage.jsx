import { useEffect, useRef, useState, useCallback } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../../AuthContext'
import { apiFetch, buildUnlockAt, capsuleTitle, formatUnlockDate } from '../../api'
import { WaxSealOlive } from '../../brand/WaxSealOlive'
import { WorkingDots } from '../../brand/WorkingDots'
import { BrandModal } from '../../components/BrandModal'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { DatePickerDropdowns } from '../../components/DatePickerDropdowns'
import { PhotoGrid } from '../../components/PhotoGrid'
import { PhotoPickerModal } from '../../components/PhotoPickerModal'

function parseDate(isoString) {
  const d = new Date(isoString)
  return { day: d.getDate(), month: d.getMonth() + 1, year: d.getFullYear() }
}

function EditAffordance({ onClick, disabled }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="text-xs text-text-muted hover:text-forest transition-colors ml-2 disabled:opacity-30"
    >
      Edit
    </button>
  )
}

function InlineError({ message = 'Couldn\'t save. Try again.' }) {
  return <p className="text-xs text-earth mt-1">{message}</p>
}

// Sealing animation — the wax seal olive appears with scale-in
function SealingOlive({ size, onComplete }) {
  const prefersReduced = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const [phase, setPhase] = useState(prefersReduced ? 'done' : 'entering')

  useEffect(() => {
    if (prefersReduced) { onComplete?.(); return }
    const t = setTimeout(() => { setPhase('done'); onComplete?.() }, 700)
    return () => clearTimeout(t)
  }, [onComplete, prefersReduced])

  return (
    <span
      style={{
        display: 'inline-block',
        transform: phase === 'entering' ? 'scale(0)' : 'scale(1)',
        opacity: phase === 'entering' ? 0 : 1,
        transition: prefersReduced ? 'none' : 'transform 700ms ease-out, opacity 700ms ease-out',
      }}
    >
      <WaxSealOlive size={size} />
    </span>
  )
}

export function CapsuleDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { apiKey } = useAuth()

  const [capsule, setCapsule] = useState(null)
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState(null)

  // edit state machine: idle | editing | saving | error
  const [editField, setEditField] = useState(null) // 'message' | 'recipients' | 'date' | 'photos'
  const [editPhase, setEditPhase] = useState('idle')
  const [editError, setEditError] = useState(null)

  // draft values for each editable field
  const [draftMessage, setDraftMessage] = useState('')
  const [draftRecipients, setDraftRecipients] = useState([''])
  const [draftDate, setDraftDate] = useState({ day: '', month: '', year: '' })
  const [draftUploadIds, setDraftUploadIds] = useState([])

  const [showSealDialog, setShowSealDialog] = useState(false)
  const [showCancelDialog, setShowCancelDialog] = useState(false)
  const [showDateModal, setShowDateModal] = useState(false)
  const [showPickerModal, setShowPickerModal] = useState(false)
  const [showDiscardDialog, setShowDiscardDialog] = useState(false)
  const [pendingNavTarget, setPendingNavTarget] = useState(null)

  // sealing animation
  const [sealingAnimation, setSealingAnimation] = useState(false)
  const [cancelTransition, setCancelTransition] = useState(false)

  const loadRef = useRef(false)

  const fetchCapsule = useCallback(async () => {
    setFetchError(null)
    try {
      const r = await apiFetch(`/api/capsules/${id}`, apiKey)
      if (r.status === 404) { setFetchError('not-found'); setLoading(false); return }
      if (!r.ok) throw new Error()
      const data = await r.json()
      setCapsule(data)
      setDraftMessage(data.message ?? '')
      setDraftRecipients(data.recipients?.length ? data.recipients : [''])
      const { day, month, year } = parseDate(data.unlock_at)
      setDraftDate({ day, month, year })
      setDraftUploadIds((data.uploads ?? []).map((u) => u.id))
    } catch {
      setFetchError('error')
    } finally {
      setLoading(false)
    }
  }, [apiKey, id])

  useEffect(() => {
    fetchCapsule()
    // Check for ?sealed=1 query param to trigger animation on arrival
    const params = new URLSearchParams(window.location.search)
    if (params.get('sealed') === '1') {
      window.history.replaceState({}, '', `/capsules/${id}`)
      setSealingAnimation(true)
    }
  }, [fetchCapsule, id])

  useEffect(() => {
    if (capsule) document.title = `${capsuleTitle(capsule.recipients)} · Heirlooms`
  }, [capsule])

  async function patchCapsule(fields) {
    const r = await apiFetch(`/api/capsules/${id}`, apiKey, {
      method: 'PATCH',
      body: JSON.stringify(fields),
    })
    if (!r.ok) throw new Error()
    return r.json()
  }

  async function saveField(field) {
    setEditPhase('saving')
    setEditError(null)
    try {
      let updated
      if (field === 'message') {
        updated = await patchCapsule({ message: draftMessage })
      } else if (field === 'recipients') {
        const filtered = draftRecipients.filter((r) => r.trim())
        if (!filtered.length) { setEditPhase('error'); setEditError('A capsule needs at least one recipient.'); return false }
        updated = await patchCapsule({ recipients: filtered })
      } else if (field === 'date') {
        if (!draftDate.day || !draftDate.month || !draftDate.year) {
          setEditPhase('error'); setEditError('"To open on" must be a full date.'); return false
        }
        updated = await patchCapsule({ unlock_at: buildUnlockAt(draftDate.day, draftDate.month, draftDate.year) })
      } else if (field === 'photos') {
        updated = await patchCapsule({ upload_ids: draftUploadIds })
      }
      setCapsule(updated)
      setDraftMessage(updated.message ?? '')
      setDraftRecipients(updated.recipients ?? [''])
      const { day, month, year } = parseDate(updated.unlock_at)
      setDraftDate({ day, month, year })
      setDraftUploadIds((updated.uploads ?? []).map((u) => u.id))
      setEditField(null)
      setEditPhase('idle')
      return true
    } catch {
      setEditPhase('error')
      setEditError(null)
      return false
    }
  }

  async function startEdit(field) {
    if (editField && editField !== field && editPhase === 'editing') {
      const ok = await saveField(editField)
      if (!ok) return
    }
    setEditField(field)
    setEditPhase('editing')
    setEditError(null)
  }

  function cancelEdit() {
    if (capsule) {
      setDraftMessage(capsule.message ?? '')
      setDraftRecipients(capsule.recipients?.length ? capsule.recipients : [''])
      const { day, month, year } = parseDate(capsule.unlock_at)
      setDraftDate({ day, month, year })
      setDraftUploadIds((capsule.uploads ?? []).map((u) => u.id))
    }
    setEditField(null)
    setEditPhase('idle')
    setEditError(null)
  }

  function handleNavAway(target) {
    if (editField && editPhase === 'editing') {
      setPendingNavTarget(target)
      setShowDiscardDialog(true)
    } else {
      navigate(target)
    }
  }

  async function handleSeal() {
    try {
      const r = await apiFetch(`/api/capsules/${id}/seal`, apiKey, { method: 'POST' })
      if (!r.ok) throw new Error()
      const updated = await r.json()
      setCapsule(updated)
      setSealingAnimation(true)
    } catch {}
    setShowSealDialog(false)
  }

  async function handleCancel() {
    try {
      const r = await apiFetch(`/api/capsules/${id}/cancel`, apiKey, { method: 'POST' })
      if (!r.ok) throw new Error()
      const updated = await r.json()
      setCapsule(updated)
      setCancelTransition(true)
    } catch {}
    setShowCancelDialog(false)
  }

  async function handlePickerDone(ids) {
    setDraftUploadIds(ids)
    setShowPickerModal(false)
    setEditPhase('saving')
    try {
      const updated = await patchCapsule({ upload_ids: ids })
      setCapsule(updated)
      setDraftUploadIds((updated.uploads ?? []).map((u) => u.id))
    } catch {
      setEditPhase('error')
    } finally {
      setEditField(null)
      setEditPhase('idle')
    }
  }

  // ---- Render helpers ----

  const isEditable = capsule && (capsule.state === 'open' || capsule.state === 'sealed')
  const isOpen = capsule?.state === 'open'
  const isSealed = capsule?.state === 'sealed'
  const isDelivered = capsule?.state === 'delivered'
  const isCancelled = capsule?.state === 'cancelled'
  const isSaving = editPhase === 'saving'

  if (loading) return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="animate-pulse space-y-4">
        <div className="h-8 bg-forest-08 rounded w-1/2" />
        <div className="h-4 bg-forest-08 rounded w-1/3" />
        <div className="h-24 bg-forest-08 rounded mt-8" />
        <div className="h-40 bg-forest-08 rounded" />
      </div>
    </div>
  )

  if (fetchError === 'not-found') return (
    <div className="max-w-3xl mx-auto px-4 py-8 text-center space-y-4">
      <p className="font-serif italic text-earth">Not found</p>
      <p className="text-sm text-text-body">This capsule doesn't exist or has been removed.</p>
      <Link to="/capsules" className="text-sm text-forest underline">Back to capsules</Link>
    </div>
  )

  if (fetchError) return (
    <div className="max-w-3xl mx-auto px-4 py-8 text-center space-y-4">
      <p className="text-earth">Couldn't load.</p>
      <p className="text-sm text-text-body">The capsule didn't load.</p>
      <div className="flex items-center justify-center gap-4">
        <button onClick={fetchCapsule} className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity">Try again</button>
        <Link to="/capsules" className="px-4 py-2 rounded-button text-sm border border-forest-25 text-forest hover:border-forest transition-colors">Back to capsules</Link>
      </div>
    </div>
  )

  const pageStyle = isDelivered
    ? { background: 'linear-gradient(180deg, #F2EEDF 0%, rgba(216,155,133,0.12) 100%)' }
    : isCancelled
    ? { background: 'rgba(181, 105, 75, 0.05)', filter: cancelTransition ? 'saturate(0.6)' : 'saturate(0.6)' }
    : {}

  const uploads = capsule.uploads ?? []
  const firstRecipient = capsule.recipients?.[0] ?? 'them'

  return (
    <main className="max-w-3xl mx-auto px-4 py-8" style={pageStyle}>
      {/* Back link */}
      <button
        onClick={() => handleNavAway('/capsules')}
        className="text-sm text-text-muted hover:text-forest transition-colors mb-8 block"
      >
        ← Capsules
      </button>

      {/* Identity block */}
      <div className="flex items-start justify-between gap-4 mb-8">
        <div className="flex-1 space-y-1">
          {/* Recipients */}
          {editField === 'recipients' ? (
            <div>
              {draftRecipients.map((r, i) => (
                <div key={i} className="flex items-center gap-2 mb-2">
                  <input
                    value={r}
                    onChange={(e) => {
                      const next = [...draftRecipients]
                      next[i] = e.target.value
                      setDraftRecipients(next)
                    }}
                    disabled={isSaving}
                    className="flex-1 px-3 py-1.5 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25 bg-parchment"
                    placeholder="Recipient name"
                  />
                  {draftRecipients.length > 1 && (
                    <button onClick={() => setDraftRecipients((prev) => prev.filter((_, j) => j !== i))}
                      className="text-text-muted hover:text-earth text-lg leading-none">×</button>
                  )}
                </div>
              ))}
              <button onClick={() => setDraftRecipients((p) => [...p, ''])}
                className="text-sm text-text-muted hover:text-forest">+ add another</button>
              {editError && <InlineError message={editError} />}
              <div className="flex gap-2 mt-2">
                <button onClick={() => saveField('recipients')} disabled={isSaving}
                  className="px-3 py-1.5 rounded-button text-sm bg-forest text-parchment hover:opacity-90 disabled:opacity-40">
                  {isSaving ? <WorkingDots size="sm" /> : 'Save recipients'}
                </button>
                <button onClick={cancelEdit} disabled={isSaving}
                  className="px-3 py-1.5 rounded-button text-sm border border-forest-25 text-forest">Cancel</button>
              </div>
            </div>
          ) : (
            <div className="flex items-baseline gap-1 flex-wrap">
              <p className="font-serif italic text-forest text-2xl leading-snug">
                {capsuleTitle(capsule.recipients)}
              </p>
              {isEditable && <EditAffordance onClick={() => startEdit('recipients')} />}
            </div>
          )}

          {/* Date */}
          <div className="flex items-baseline gap-1 flex-wrap">
            <p className="text-sm text-forest">To open on {formatUnlockDate(capsule.unlock_at)}</p>
            {isEditable && <EditAffordance onClick={() => { startEdit('date'); setShowDateModal(true) }} />}
          </div>

          {isDelivered && capsule.delivered_at && (
            <p className="text-sm text-forest">Delivered on {formatUnlockDate(capsule.delivered_at)}</p>
          )}
          {isCancelled && capsule.cancelled_at && (
            <p className="text-sm text-forest">Cancelled on {formatUnlockDate(capsule.cancelled_at)}</p>
          )}
        </div>

        {/* Wax seal olive — ceremonial size for sealed */}
        {isSealed && (
          <div className="flex-shrink-0">
            {sealingAnimation ? (
              <SealingOlive size={56} onComplete={() => setSealingAnimation(false)} />
            ) : (
              <WaxSealOlive size={56} />
            )}
          </div>
        )}

        {/* Delivered: backdrop-size olive, low opacity */}
        {isDelivered && (
          <div className="flex-shrink-0 opacity-25">
            <WaxSealOlive size={140} />
          </div>
        )}
      </div>

      {/* Message */}
      <div className="mb-8">
        {editField === 'message' ? (
          <div>
            <textarea
              value={draftMessage}
              onChange={(e) => setDraftMessage(e.target.value)}
              disabled={isSaving}
              rows={6}
              className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm bg-parchment focus:outline-none focus:ring-2 focus:ring-forest-25 resize-none"
              placeholder="Write a message…"
            />
            {editError && <InlineError message={editError} />}
            <div className="flex gap-2 mt-2">
              <button onClick={() => saveField('message')} disabled={isSaving}
                className="px-3 py-1.5 rounded-button text-sm bg-forest text-parchment hover:opacity-90 disabled:opacity-40">
                {isSaving ? <WorkingDots size="sm" /> : 'Save message'}
              </button>
              <button onClick={cancelEdit} disabled={isSaving}
                className="px-3 py-1.5 rounded-button text-sm border border-forest-25 text-forest">Cancel</button>
            </div>
          </div>
        ) : (
          <div>
            {capsule.message ? (
              <p className={`text-base text-forest leading-relaxed ${isSealed || isDelivered ? 'font-serif italic' : ''}`}>
                {capsule.message}
              </p>
            ) : (
              <p className="text-sm font-serif italic text-text-muted">No message.</p>
            )}
            {isEditable && (
              <button onClick={() => startEdit('message')}
                className="text-xs text-text-muted hover:text-forest transition-colors mt-2 block">
                Edit message
              </button>
            )}
          </div>
        )}
      </div>

      {/* Photo grid */}
      <div className="mb-8">
        {uploads.length === 0 ? (
          isOpen ? (
            <button onClick={() => { startEdit('photos'); setShowPickerModal(true) }}
              className="w-full py-4 rounded-card border border-dashed border-forest-25 text-sm text-text-muted hover:border-forest hover:text-forest transition-colors">
              Choose what to include
            </button>
          ) : null
        ) : (
          <div>
            <PhotoGrid
              uploads={uploads}
              getPhotoHref={(uploadId) => `/photos/${uploadId}`}
              cols="3"
            />
            {isOpen && (
              <button onClick={() => { startEdit('photos'); setShowPickerModal(true) }}
                className="text-xs text-text-muted hover:text-forest transition-colors mt-3 block">
                Add or remove items
              </button>
            )}
          </div>
        )}
      </div>

      {/* Action region */}
      {isOpen && (
        <div className="flex items-center justify-between pt-4 border-t border-forest-08">
          <button onClick={() => setShowCancelDialog(true)}
            className="px-4 py-2 rounded-button text-sm border border-earth text-earth hover:bg-earth-10 transition-colors">
            Cancel capsule
          </button>
          <button onClick={() => setShowSealDialog(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity">
            <WaxSealOlive size={14} className="text-bloom" />
            Seal capsule
          </button>
        </div>
      )}

      {isSealed && (
        <div className="pt-4 border-t border-forest-08">
          <button onClick={() => setShowCancelDialog(true)}
            className="px-4 py-2 rounded-button text-sm border border-earth text-earth hover:bg-earth-10 transition-colors">
            Cancel capsule
          </button>
        </div>
      )}

      {/* Modals */}
      {showSealDialog && (
        <ConfirmDialog
          title="Seal this capsule?"
          titleItalic
          body="Once sealed, you can't add or remove items. You'll still be able to edit the message, recipients, and date until it opens."
          primaryLabel={<span className="flex items-center gap-2"><WaxSealOlive size={12} />Seal capsule</span>}
          cancelLabel="Cancel"
          onConfirm={handleSeal}
          onCancel={() => setShowSealDialog(false)}
          focusPrimary
        />
      )}

      {showCancelDialog && (
        <ConfirmDialog
          title="Cancel this capsule?"
          titleItalic
          body={capsule.recipients?.length === 1
            ? `${firstRecipient} won't receive it. This can't be undone.`
            : "They won't receive it. This can't be undone."}
          primaryLabel="Cancel capsule"
          primaryClass="bg-earth text-parchment hover:opacity-90"
          cancelLabel="Keep capsule"
          onConfirm={handleCancel}
          onCancel={() => setShowCancelDialog(false)}
          focusPrimary={false}
        />
      )}

      {showDiscardDialog && (
        <ConfirmDialog
          title="Discard changes?"
          titleItalic={false}
          body={
            editField === 'message' ? "Your edits to the message haven't been saved." :
            editField === 'recipients' ? "Your edits to the recipients haven't been saved." :
            editField === 'date' ? "Your edits to the date haven't been saved." :
            "Your photo changes haven't been saved."
          }
          primaryLabel="Discard"
          primaryClass="bg-earth text-parchment hover:opacity-90"
          cancelLabel="Keep editing"
          onConfirm={() => { setShowDiscardDialog(false); setEditField(null); setEditPhase('idle'); navigate(pendingNavTarget) }}
          onCancel={() => setShowDiscardDialog(false)}
          focusPrimary={false}
        />
      )}

      {showDateModal && (
        <BrandModal onClose={() => { setShowDateModal(false); cancelEdit() }} width="max-w-sm">
          <div className="p-6 space-y-4">
            <h2 className="text-base font-sans font-medium text-forest">Edit unlock date</h2>
            <DatePickerDropdowns
              day={draftDate.day}
              month={draftDate.month}
              year={draftDate.year}
              onChange={(d) => setDraftDate(d)}
            />
            {editError && <InlineError message={editError} />}
            <div className="flex gap-2">
              <button
                onClick={async () => { const ok = await saveField('date'); if (ok) setShowDateModal(false) }}
                disabled={isSaving || !draftDate.day || !draftDate.month || !draftDate.year}
                className="px-3 py-1.5 rounded-button text-sm bg-forest text-parchment hover:opacity-90 disabled:opacity-40">
                {isSaving ? <WorkingDots size="sm" /> : 'Save date'}
              </button>
              <button onClick={() => { setShowDateModal(false); cancelEdit() }}
                className="px-3 py-1.5 rounded-button text-sm border border-forest-25 text-forest">Cancel</button>
            </div>
          </div>
        </BrandModal>
      )}

      {showPickerModal && (
        <PhotoPickerModal
          initialSelectedIds={(capsule.uploads ?? []).map((u) => u.id)}
          onDone={handlePickerDone}
          onCancel={() => { setShowPickerModal(false); setEditField(null); setEditPhase('idle') }}
        />
      )}
    </main>
  )
}
