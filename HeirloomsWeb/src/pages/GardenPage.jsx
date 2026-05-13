import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BrandModal } from '../components/BrandModal'
import { OliveBranchArrival } from '../brand/OliveBranchArrival'
import { UploadThumb } from '../components/UploadThumb'
import {
  DndContext,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  closestCenter,
} from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useAuth } from '../AuthContext'
import { API_URL, apiFetch, initiateEncryptedUpload, initiateResumableUpload, putBlob, putBlobWithProgress, confirmEncryptedUpload, fetchSettings } from '../api'
import { ShareModal } from '../components/ShareModal'
import { WorkingDots } from '../brand/WorkingDots'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { getMasterKey, getSharingPrivkey } from '../crypto/vaultSession'
import {
  generateDek, encryptSymmetric, wrapDekUnderMasterKey, ALG_AES256GCM_V1, ALG_MASTER_AES256GCM_V1,
  decryptSymmetric, unwrapDekWithMasterKey, fromB64, toB64, aesGcmEncryptWithAad,
  generatePlotKey, wrapPlotKeyForMember,
} from '../crypto/vaultCrypto'
import { InviteMemberModal } from '../components/InviteMemberModal'
import { parse as parseExif } from 'exifr'

const JUST_ARRIVED_SENTINEL = '__just_arrived__'

// ---- Thumbnail card for horizontal plot row --------------------------------

function PlotThumbCard({ upload, apiKey, onTagClick, onVideoPlay, onRotate, onImagePreview, onCompostClick, onShareClick, isNew, onArrivalComplete }) {
  const navigate = useNavigate()
  const isImage = upload.mimeType?.startsWith('image/')
  const isVideo = upload.mimeType?.startsWith('video/')
  const isComposted = !!upload.compostedAt
  const isEncrypted = upload.storageClass === 'encrypted'
  const canShare = isEncrypted && !upload.sharedFromUserId && !isComposted

  const saturate = isComposted ? { filter: 'saturate(0.4) opacity(0.7)' } : {}

  function stop(e, fn) { e.preventDefault(); e.stopPropagation(); fn() }

  const thumbnailContent = (
    <div className="relative w-full h-full">
      <UploadThumb
        upload={upload}
        className="w-full h-full object-contain"
        style={isComposted ? saturate : undefined}
        rotation={upload.rotation}
        alt=""
      />
      {isVideo && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/25 pointer-events-none">
          <div className="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center">
            <svg className="w-5 h-5 text-white ml-0.5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </div>
        </div>
      )}
    </div>
  )

  return (
    <div className="flex-shrink-0 w-40 h-40 relative group">
      {/* Encrypted uploads: always navigate to detail page (no quick modal for ciphertext) */}
      {isVideo && onVideoPlay && !isEncrypted ? (
        <button
          onClick={(e) => stop(e, () => onVideoPlay(upload))}
          className="w-full h-full rounded overflow-hidden border border-forest-08 bg-forest-04 block cursor-pointer relative"
        >
          {thumbnailContent}
        </button>
      ) : onImagePreview && !isEncrypted ? (
        <button
          onClick={(e) => stop(e, () => onImagePreview(upload))}
          className="w-full h-full rounded overflow-hidden border border-forest-08 bg-forest-04 block cursor-pointer relative"
        >
          {thumbnailContent}
        </button>
      ) : (
        <Link
          to={`/photos/${upload.id}?from=garden`}
          state={{ upload }}
          className="w-full h-full rounded overflow-hidden border border-forest-08 bg-forest-04 block hover:opacity-90 transition-opacity relative"
        >
          {thumbnailContent}
        </Link>
      )}

      {/* Rotate button — images only, appears on hover */}
      {isImage && onRotate && (
        <button
          onClick={(e) => stop(e, () => onRotate(upload.id))}
          className="absolute top-1 left-1 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity bg-black/40 rounded p-0.5"
          title="Rotate 90°"
          aria-label="Rotate 90°"
        >
          <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      )}

      {/* Pencil — go to detail page, appears on hover next to tag button */}
      <button
        onClick={(e) => stop(e, () => navigate(`/photos/${upload.id}?from=garden`, { state: { upload } }))}
        className="absolute top-1 right-7 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity bg-black/40 rounded p-0.5"
        title="View details"
        aria-label="View details"
      >
        <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
        </svg>
      </button>

      {/* Tag quick action — appears on hover */}
      {onTagClick && !isComposted && (
        <button
          onClick={(e) => stop(e, () => onTagClick(upload))}
          className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity bg-black/40 rounded p-0.5"
          title="Edit tags"
          aria-label="Edit tags"
        >
          <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A2 2 0 013 12V7a2 2 0 012-2h2z" />
          </svg>
        </button>
      )}

      {/* Compost — bottom-left, destructive colour, appears on hover */}
      {onCompostClick && !isComposted && !upload.tags?.length && (
        <button
          onClick={(e) => stop(e, () => onCompostClick(upload))}
          className="absolute bottom-1 left-1 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity bg-earth/70 rounded p-0.5"
          title="Compost"
          aria-label="Compost"
        >
          <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      )}

      {/* Share — bottom-right, appears on hover, owned encrypted items only */}
      {canShare && onShareClick && (
        <button
          onClick={(e) => stop(e, () => onShareClick(upload))}
          className="absolute bottom-1 right-1 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity bg-black/40 rounded p-0.5"
          title="Share with a friend"
          aria-label="Share with a friend"
        >
          <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
          </svg>
        </button>
      )}

      {/* Arrival animation overlay — shown for newly arrived items */}
      {isNew && (
        <div
          className="absolute inset-0 rounded overflow-hidden flex items-center justify-center pointer-events-none"
          style={{ background: 'rgba(242, 238, 223, 0.88)' }}
        >
          <OliveBranchArrival withWordmark={false} width={80} onComplete={onArrivalComplete} />
        </div>
      )}
    </div>
  )
}

// ---- Horizontal scrolling row of items for one plot ------------------------

function PlotItemsRow({ plot, apiKey, onTagClick, onVideoPlay, onImagePreview, onCompostClick, onShareClick, refreshKey, excludeIds }) {
  const [items, setItems] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [newlyArrivedIds, setNewlyArrivedIds] = useState(new Set())
  const rowRef = useRef(null)
  // Tracks IDs seen in the last fetch; null until the initial load completes.
  const knownIdsRef = useRef(null)
  const isJustArrived = plot.name === JUST_ARRIVED_SENTINEL

  const buildUrl = useCallback((cursor) => {
    const params = new URLSearchParams({ limit: '50', plot_id: plot.id })
    if (cursor) params.set('cursor', cursor)
    return `/api/content/uploads?${params}`
  }, [plot.id])

  // Full reload (shows loading state) — for initial mount and plot criteria changes
  useEffect(() => {
    setLoading(true)
    setItems([])
    setNextCursor(null)
    knownIdsRef.current = null
    apiFetch(buildUrl(null), apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data) => {
        const newItems = data.items ?? []
        setItems(newItems)
        setNextCursor(data.next_cursor ?? null)
        // Seed known IDs — no animation on initial load.
        if (isJustArrived) knownIdsRef.current = new Set(newItems.map((i) => i.id))
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [buildUrl, apiKey])

  // Silent re-fetch triggered externally (refreshKey) — swaps items without loading flash
  useEffect(() => {
    if (refreshKey === 0) return
    apiFetch(buildUrl(null), apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject())
      .then((data) => {
        const newItems = data.items ?? []
        setItems(newItems)
        setNextCursor(data.next_cursor ?? null)
        if (isJustArrived && knownIdsRef.current !== null) {
          const arrived = newItems.filter((i) => !knownIdsRef.current.has(i.id))
          if (arrived.length > 0) {
            setNewlyArrivedIds((prev) => new Set([...prev, ...arrived.map((i) => i.id)]))
          }
          knownIdsRef.current = new Set(newItems.map((i) => i.id))
        }
      })
      .catch(() => {})
  }, [refreshKey])

  // Optimistic exclusion: items removed immediately by the parent (e.g. after tagging)
  const visibleItems = excludeIds?.size ? items.filter((u) => !excludeIds.has(u.id)) : items

  function handleRotateItem(uploadId) {
    setItems((prev) => prev.map((u) => {
      if (u.id !== uploadId) return u
      const newRotation = ((u.rotation ?? 0) + 90) % 360
      apiFetch(`/api/content/uploads/${uploadId}/rotation`, apiKey, {
        method: 'PATCH',
        body: JSON.stringify({ rotation: newRotation }),
      }).catch(() => {})
      return { ...u, rotation: newRotation }
    }))
  }

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) return
    setLoadingMore(true)
    try {
      const data = await apiFetch(buildUrl(nextCursor), apiKey).then((r) => r.ok ? r.json() : Promise.reject())
      setItems((prev) => [...prev, ...(data.items ?? [])])
      setNextCursor(data.next_cursor ?? null)
    } catch {}
    setLoadingMore(false)
  }

  if (loading) {
    return (
      <div className="h-40 flex items-center px-4">
        <WorkingDots size="md" />
      </div>
    )
  }

  if (visibleItems.length === 0) {
    const emptyMsg = plot.name === JUST_ARRIVED_SENTINEL
      ? 'Nothing new to tend.'
      : "No items match this plot's criteria yet."
    return (
      <div className="h-24 flex items-center px-1">
        <p className="font-serif italic text-text-muted text-sm">{emptyMsg}</p>
      </div>
    )
  }

  return (
    <div ref={rowRef} className="flex gap-3 overflow-x-auto pb-2 scrollbar-thin">
      {visibleItems.map((upload) => (
        <PlotThumbCard key={upload.id} upload={upload} apiKey={apiKey}
          onTagClick={onTagClick} onVideoPlay={onVideoPlay} onImagePreview={onImagePreview} onCompostClick={onCompostClick} onShareClick={onShareClick} onRotate={handleRotateItem}
          isNew={newlyArrivedIds.has(upload.id)}
          onArrivalComplete={() => setNewlyArrivedIds((prev) => {
            const next = new Set(prev); next.delete(upload.id); return next
          })} />
      ))}
      {nextCursor && (
        <button
          onClick={handleLoadMore}
          disabled={loadingMore}
          className="flex-shrink-0 w-24 h-40 rounded border border-forest-15 text-forest text-xs font-sans hover:bg-forest-04 transition-colors disabled:opacity-40 flex items-center justify-center"
        >
          {loadingMore ? <WorkingDots size="sm" /> : 'More'}
        </button>
      )}
    </div>
  )
}

// ---- Gear icon SVG ---------------------------------------------------------

function GearIcon() {
  return (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

function DragHandleIcon() {
  return (
    <svg className="w-4 h-4 text-text-muted" fill="currentColor" viewBox="0 0 24 24">
      <circle cx="9" cy="7" r="1.5" /><circle cx="15" cy="7" r="1.5" />
      <circle cx="9" cy="12" r="1.5" /><circle cx="15" cy="12" r="1.5" />
      <circle cx="9" cy="17" r="1.5" /><circle cx="15" cy="17" r="1.5" />
    </svg>
  )
}

// ---- Gear menu for user-defined plots --------------------------------------

function PlotGearMenu({ plot, isFirst, isLast, onEdit, onDelete, onMoveUp, onMoveDown, onManageMembers }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    function handleClick(e) { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="p-1 text-text-muted hover:text-forest transition-colors rounded"
        title="Plot options"
        aria-label="Plot options"
      >
        <GearIcon />
      </button>
      {open && (
        <div className="absolute right-0 top-7 z-20 w-36 bg-white border border-forest-15 rounded shadow-md text-sm py-1">
          {plot.visibility === 'shared' && onManageMembers && (
            <button className="w-full text-left px-3 py-1.5 hover:bg-forest-04 text-forest"
              onClick={() => { setOpen(false); onManageMembers() }}>Manage members</button>
          )}
          {plot.visibility !== 'shared' && (
          <button className="w-full text-left px-3 py-1.5 hover:bg-forest-04 text-forest"
            onClick={() => { setOpen(false); onEdit() }}>Edit</button>
          )}
          <button className="w-full text-left px-3 py-1.5 hover:bg-forest-04 text-forest disabled:opacity-40"
            disabled={isFirst}
            onClick={() => { setOpen(false); onMoveUp() }}>Move up</button>
          <button className="w-full text-left px-3 py-1.5 hover:bg-forest-04 text-forest disabled:opacity-40"
            disabled={isLast}
            onClick={() => { setOpen(false); onMoveDown() }}>Move down</button>
          <div className="border-t border-forest-08 my-1" />
          <button className="w-full text-left px-3 py-1.5 hover:bg-earth-10 text-earth"
            onClick={() => { setOpen(false); onDelete() }}>Delete</button>
        </div>
      )}
    </div>
  )
}

// ---- Quick tag modal (from garden thumbnail) --------------------------------

function QuickTagModal({ upload, apiKey, onSave, onClose }) {
  const [selected, setSelected] = useState([...(upload.tags ?? [])])
  const [input, setInput] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const inputRef = useRef(null)
  const suppressBlurRef = useRef(false)

  useEffect(() => { inputRef.current?.focus() }, [])

  function addTag(tag) {
    const t = tag.trim().toLowerCase()
    if (t && !selected.includes(t)) setSelected((prev) => [...prev, t])
    setInput('')
    inputRef.current?.focus()
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') { e.preventDefault(); if (input.trim()) addTag(input.trim()) }
    else if (e.key === 'Backspace' && !input && selected.length > 0) setSelected((prev) => prev.slice(0, -1))
  }

  async function handleSave() {
    const pending = input.trim().toLowerCase()
    const finalTags = pending && !selected.includes(pending) ? [...selected, pending] : selected
    setSaving(true); setError(null)
    try { await onSave(upload.id, finalTags) } catch (err) { setError(err.message) } finally { setSaving(false) }
  }

  return (
    <BrandModal onClose={onClose} width="max-w-xs">
      <div className="p-4 space-y-3">
        <p className="text-sm font-sans text-forest font-medium">Edit tags</p>
        <p className="text-xs text-text-muted truncate">{upload.storageKey}</p>

        {selected.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {selected.map((tag) => (
              <span key={tag} className="inline-flex items-center gap-1 px-[9px] py-[3px] rounded-chip bg-forest-08 text-forest text-[11px]">
                {tag}
                <button type="button" onMouseDown={(e) => e.preventDefault()}
                  onClick={() => setSelected((prev) => prev.filter((t) => t !== tag))}
                  className="text-text-muted text-[13px] leading-none ml-0.5">×</button>
              </span>
            ))}
          </div>
        )}

        <input ref={inputRef} value={input}
          onChange={(e) => { setInput(e.target.value) }}
          onFocus={() => {}}
          onBlur={() => { if (!suppressBlurRef.current && input.trim()) addTag(input.trim()) }}
          onKeyDown={handleKeyDown}
          placeholder="Add tag… (Enter to confirm)"
          autoComplete="off" disabled={saving}
          className="w-full text-xs border border-forest-15 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent" />

        {error && <p className="text-xs text-earth">{error}</p>}

        <div className="flex gap-2 pt-1">
          <button onClick={handleSave} disabled={saving}
            className="px-3 py-1.5 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
            {saving ? '…' : 'Save'}
          </button>
          <button onClick={onClose} disabled={saving}
            className="px-3 py-1.5 text-text-muted hover:text-forest transition-colors text-sm">
            Cancel
          </button>
        </div>
      </div>
    </BrandModal>
  )
}

// ---- Quick video modal (from garden thumbnail) ------------------------------

function QuickVideoModal({ upload, apiKey, onClose }) {
  const [videoSrc, setVideoSrc] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    // Try signed URL for direct streaming; fall back to proxy download
    apiFetch(`/api/content/uploads/${upload.id}/url`, apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject())
      .then((data) => { setVideoSrc(data.url); setLoading(false) })
      .catch(() => {
        fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, { headers: { 'X-Api-Key': apiKey } })
          .then((r) => r.ok ? r.blob() : Promise.reject())
          .then((blob) => { setVideoSrc(URL.createObjectURL(blob)); setLoading(false) })
          .catch(() => { setError(true); setLoading(false) })
      })
  }, [upload.id, apiKey])

  return (
    <BrandModal onClose={onClose} width="max-w-2xl">
      <div className="p-2">
        {loading && (
          <div className="flex justify-center items-center py-12">
            <WorkingDots size="lg" label="Loading video…" />
          </div>
        )}
        {error && (
          <p className="text-center font-serif italic text-earth py-8">
            Couldn't load video. <Link to={`/photos/${upload.id}?from=garden`} state={{ upload }} className="underline" onClick={onClose}>Open detail page</Link>
          </p>
        )}
        {videoSrc && (
          <video src={videoSrc} controls autoPlay className="w-full rounded max-h-[75vh]" />
        )}
      </div>
    </BrandModal>
  )
}

// ---- Quick image preview modal (from garden thumbnail) ---------------------

function QuickImageModal({ upload, apiKey, onClose }) {
  const [blobUrl, setBlobUrl] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    let ownUrl = null
    fetch(`${API_URL}/api/content/uploads/${upload.id}/file`, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject())
      .then((blob) => { ownUrl = URL.createObjectURL(blob); setBlobUrl(ownUrl); setLoading(false) })
      .catch(() => { setError(true); setLoading(false) })
    return () => { if (ownUrl) URL.revokeObjectURL(ownUrl) }
  }, [upload.id, apiKey])

  const rotate = upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : {}

  return (
    <BrandModal onClose={onClose} width="max-w-3xl">
      <div className="p-2 flex items-center justify-center min-h-[200px]">
        {loading && <WorkingDots size="lg" />}
        {error && (
          <p className="font-serif italic text-earth py-8">Couldn't load image.</p>
        )}
        {blobUrl && (
          <img src={blobUrl} alt="" className="max-w-full max-h-[75vh] object-contain" style={rotate} />
        )}
      </div>
    </BrandModal>
  )
}

// ---- System (Just arrived) plot row — no DnD, no gear ----------------------

function SystemPlotRow({ plot, apiKey, onTagClick, onVideoPlay, onImagePreview, onCompostClick, onShareClick, refreshKey, excludeIds }) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <h2 className="font-serif italic text-forest text-base">Just arrived</h2>
      </div>
      <PlotItemsRow plot={plot} apiKey={apiKey}
        onTagClick={onTagClick} onVideoPlay={onVideoPlay} onImagePreview={onImagePreview} onCompostClick={onCompostClick} onShareClick={onShareClick} refreshKey={refreshKey} excludeIds={excludeIds} />
    </div>
  )
}

// ---- Sortable user plot row ------------------------------------------------

function SortablePlotRow({ plot, isFirst, isLast, apiKey, onEdit, onDelete, onMoveUp, onMoveDown, onManageMembers, onTagClick, onVideoPlay, onImagePreview, onCompostClick, onShareClick, refreshKey, excludeIds }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: plot.id })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <div ref={setNodeRef} style={style} className="space-y-2">
      <div className="flex items-center gap-2">
        <button
          {...attributes}
          {...listeners}
          className="cursor-grab active:cursor-grabbing p-0.5 -ml-0.5 rounded hover:bg-forest-04 transition-colors touch-none"
          title="Drag to reorder"
          aria-label="Drag to reorder"
        >
          <DragHandleIcon />
        </button>
        <h2 className="font-sans text-forest text-sm font-medium flex-1 flex items-center gap-1.5">
          {!plot.criteria && <span title="Collection plot" className="text-[10px] text-text-muted font-normal border border-forest-15 rounded px-1">collection</span>}
          {plot.name}
        </h2>
        {plot.visibility === 'shared' && (
          <span title="Shared plot" className="text-[10px] text-text-muted font-normal border border-forest-15 rounded px-1">shared</span>
        )}
        <PlotGearMenu
          plot={plot}
          isFirst={isFirst}
          isLast={isLast}
          onEdit={onEdit}
          onDelete={onDelete}
          onMoveUp={onMoveUp}
          onMoveDown={onMoveDown}
          onManageMembers={onManageMembers}
        />
      </div>
      <PlotItemsRow plot={plot} apiKey={apiKey}
        onTagClick={onTagClick} onVideoPlay={onVideoPlay} onImagePreview={onImagePreview} onCompostClick={onCompostClick} onShareClick={onShareClick} refreshKey={refreshKey} excludeIds={excludeIds} />
    </div>
  )
}

// ---- Main GardenPage -------------------------------------------------------

async function getVideoDurationSeconds(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      resolve(isFinite(video.duration) ? Math.round(video.duration) : null)
    }
    video.onerror = () => { URL.revokeObjectURL(url); resolve(null) }
    video.src = url
  })
}

async function generateThumbnail(file) {
  const MAX_DIM = 400
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  try {
    if (file.type.startsWith('image/')) {
      const img = await createImageBitmap(file)
      const scale = Math.min(1, MAX_DIM / Math.max(img.width, img.height))
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      img.close()
    } else {
      const url = URL.createObjectURL(file)
      const video = document.createElement('video')
      video.src = url
      video.muted = true
      await new Promise((resolve) => { video.onloadeddata = resolve; video.load() })
      video.currentTime = 1
      await new Promise((resolve) => { video.onseeked = resolve })
      const scale = Math.min(1, MAX_DIM / Math.max(video.videoWidth || 1, video.videoHeight || 1))
      canvas.width = Math.round((video.videoWidth || 1) * scale)
      canvas.height = Math.round((video.videoHeight || 1) * scale)
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
    }
    return await new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (!blob) { reject(new Error('thumbnail generation failed')); return }
        blob.arrayBuffer().then((ab) => resolve(new Uint8Array(ab)))
      }, 'image/jpeg', 0.8)
    })
  } catch {
    // 1×1 white JPEG fallback
    return new Uint8Array([
      0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
      0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xff, 0xdb, 0x00, 0x43,
      0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09,
      0x09, 0x08, 0x0a, 0x0c, 0x14, 0x0d, 0x0c, 0x0b, 0x0b, 0x0c, 0x19, 0x12,
      0x13, 0x0f, 0x14, 0x1d, 0x1a, 0x1f, 0x1e, 0x1d, 0x1a, 0x1c, 0x1c, 0x20,
      0x24, 0x2e, 0x27, 0x20, 0x22, 0x2c, 0x23, 0x1c, 0x1c, 0x28, 0x37, 0x29,
      0x2c, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1f, 0x27, 0x39, 0x3d, 0x38, 0x32,
      0x3c, 0x2e, 0x33, 0x34, 0x32, 0xff, 0xc0, 0x00, 0x0b, 0x08, 0x00, 0x01,
      0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xff, 0xc4, 0x00, 0x1f, 0x00, 0x00,
      0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
      0x09, 0x0a, 0x0b, 0xff, 0xc4, 0x00, 0xb5, 0x10, 0x00, 0x02, 0x01, 0x03,
      0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00, 0x01, 0x7d,
      0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06,
      0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
      0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72,
      0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
      0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45,
      0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
      0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75,
      0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
      0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3,
      0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
      0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9,
      0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
      0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4,
      0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa, 0xff, 0xda, 0x00, 0x08, 0x01, 0x01,
      0x00, 0x00, 0x3f, 0x00, 0xfb, 0xd3, 0xff, 0xd9,
    ])
  }
}

// Plaintext chunk size: 1 MiB cipher chunks (1 MiB - 28 bytes overhead).
// Value stored per-upload so the reader always knows what size was used.
const PLAIN_CHUNK_SIZE = 1 * 1024 * 1024 - 28
const LARGE_FILE_THRESHOLD = 10 * 1024 * 1024

let ffmpegInstance = null
async function getFFmpeg() {
  if (ffmpegInstance) return ffmpegInstance
  const { FFmpeg } = await import('@ffmpeg/ffmpeg')
  const { toBlobURL } = await import('@ffmpeg/util')
  const ff = new FFmpeg()
  await ff.load({
    coreURL: await toBlobURL('/ffmpeg-core.js', 'text/javascript'),
    wasmURL: await toBlobURL('/ffmpeg-core.wasm', 'application/wasm'),
  })
  ffmpegInstance = ff
  return ff
}

async function generatePreviewClip(file, durationSeconds) {
  const { fetchFile } = await import('@ffmpeg/util')
  const ff = await getFFmpeg()
  await ff.writeFile('input', await fetchFile(file))
  await ff.exec([
    '-i', 'input',
    '-t', String(durationSeconds),
    '-c:v', 'copy',
    '-c:a', 'copy',
    '-movflags', '+faststart',
    '-y', 'preview.mp4',
  ])
  const data = await ff.readFile('preview.mp4')
  await ff.deleteFile('input')
  await ff.deleteFile('preview.mp4')
  return data instanceof Uint8Array ? data : new Uint8Array(data)
}

function computeTotalCiphertextSize(fileSize, plainChunkSize) {
  const numChunks = Math.ceil(fileSize / plainChunkSize)
  return numChunks * 28 + fileSize
}

// Deterministic 12-byte nonce: [4-byte uploadId prefix][8-byte big-endian chunkIndex]
function buildChunkNonce(uploadIdPrefix, chunkIndex) {
  const nonce = new Uint8Array(12)
  nonce.set(uploadIdPrefix.slice(0, 4), 0)
  const v = new DataView(nonce.buffer)
  v.setUint32(4, Math.floor(chunkIndex / 0x100000000), false)
  v.setUint32(8, chunkIndex >>> 0, false)
  return nonce
}

function buildChunkAad(uploadIdPrefix, chunkIndex) {
  return buildChunkNonce(uploadIdPrefix, chunkIndex)
}

function concatArrays(...arrays) {
  const total = arrays.reduce((s, a) => s + a.length, 0)
  const out = new Uint8Array(total)
  let offset = 0
  for (const a of arrays) { out.set(a, offset); offset += a.length }
  return out
}

async function encryptAndUploadStreamingContent(file, contentDek, storageKey, resumableUri, totalCiphertextBytes, plainChunkSize, onProgress) {
  const uploadIdPrefix = new TextEncoder().encode(storageKey).slice(0, 4)

  let ciphertextOffset = 0
  let chunkIndex = 0
  let fileOffset = 0

  while (fileOffset < file.size) {
    const slice = file.slice(fileOffset, fileOffset + plainChunkSize)
    const plainChunk = new Uint8Array(await slice.arrayBuffer())

    const nonce = buildChunkNonce(uploadIdPrefix, chunkIndex)
    const aad = buildChunkAad(uploadIdPrefix, chunkIndex)
    const cipherChunk = await aesGcmEncryptWithAad(contentDek, nonce, aad, plainChunk)

    const chunkBytes = concatArrays(nonce, cipherChunk)

    const chunkStart = ciphertextOffset
    const chunkEnd = ciphertextOffset + chunkBytes.length - 1
    const isLast = (chunkEnd + 1) === totalCiphertextBytes

    const r = await fetch(resumableUri, {
      method: 'PUT',
      body: chunkBytes,
      headers: { 'Content-Range': `bytes ${chunkStart}-${chunkEnd}/${totalCiphertextBytes}` },
    })

    if (isLast ? !r.ok : r.status !== 308) {
      throw new Error(`Chunk ${chunkIndex} PUT failed: ${r.status}`)
    }

    ciphertextOffset += chunkBytes.length
    fileOffset += plainChunk.length
    chunkIndex++
    onProgress && onProgress(fileOffset)
  }
}

async function buildEncryptedMetadata(file) {
  let exif = null
  if (file.type.startsWith('image/')) {
    try {
      exif = await parseExif(file, {
        gps: true,
        exif: true,
        ifd0: true,
        pick: ['GPSLatitude', 'GPSLongitude', 'GPSAltitude', 'Make', 'Model',
               'LensModel', 'FocalLength', 'ISO', 'ExposureTime', 'FNumber'],
      })
    } catch { /* leave null — all-null blob is valid */ }
  }
  const et = exif?.ExposureTime ?? null
  return JSON.stringify({
    v: 1,
    gps_lat:         exif?.latitude      ?? null,
    gps_lon:         exif?.longitude     ?? null,
    gps_alt:         exif?.GPSAltitude   ?? null,
    camera_make:     exif?.Make          ?? null,
    camera_model:    exif?.Model         ?? null,
    lens_model:      exif?.LensModel     ?? null,
    focal_length_mm: exif?.FocalLength   ?? null,
    iso:             exif?.ISO           ?? null,
    exposure_num:    et !== null ? (et >= 1 ? Math.round(et) : 1) : null,
    exposure_den:    et !== null ? (et >= 1 ? 1 : Math.round(1 / et)) : null,
    aperture:        exif?.FNumber       ?? null,
  })
}

async function encryptAndUpload(file, apiKey, onStatus) {
  const settings = await fetchSettings(apiKey)
  const previewDurationSeconds = settings.previewDurationSeconds ?? 15
  const isLarge = file.size > LARGE_FILE_THRESHOLD
  const isVideo = file.type.startsWith('video/')
  const durationSeconds = isVideo ? await getVideoDurationSeconds(file) : null

  const contentDek = generateDek()
  const thumbDek = generateDek()
  const masterKey = getMasterKey()

  // Step 1: initiate content + thumbnail upload slots
  onStatus('Preparing…')
  const { storageKey, uploadUrl, thumbnailStorageKey, thumbnailUploadUrl } =
    await initiateEncryptedUpload(apiKey, file.type)

  // Step 2: initiate preview upload slot (large videos only)
  let previewInit = null
  if (isVideo && isLarge) {
    previewInit = await initiateEncryptedUpload(apiKey, 'video/mp4')
  }

  // Step 3: encrypt + upload content (streaming for large files, envelope for small)
  let contentFileSize
  let usedChunkSize = null
  if (isLarge) {
    const totalCiphertextBytes = computeTotalCiphertextSize(file.size, PLAIN_CHUNK_SIZE)
    const { resumableUri } = await initiateResumableUpload(apiKey, storageKey, totalCiphertextBytes, 'application/octet-stream')
    await encryptAndUploadStreamingContent(file, contentDek, storageKey, resumableUri, totalCiphertextBytes, PLAIN_CHUNK_SIZE, (consumed) => {
      onStatus(`Uploading (${Math.round(consumed / file.size * 100)}%)…`)
    })
    contentFileSize = file.size
    usedChunkSize = PLAIN_CHUNK_SIZE
  } else {
    onStatus('Encrypting…')
    const fileBytes = new Uint8Array(await file.arrayBuffer())
    const contentEnvelope = await encryptSymmetric(ALG_AES256GCM_V1, contentDek, fileBytes)
    contentFileSize = fileBytes.length
    onStatus('Uploading…')
    await putBlobWithProgress(uploadUrl, contentEnvelope, (loaded) => {
      onStatus(`Uploading (${Math.round(loaded / contentEnvelope.length * 100)}%)…`)
    })
  }

  // Step 4: thumbnail (always small, always envelope)
  const thumbBytes = await generateThumbnail(file)
  const thumbEnvelope = await encryptSymmetric(ALG_AES256GCM_V1, thumbDek, thumbBytes)
  await putBlob(thumbnailUploadUrl, thumbEnvelope)

  // Step 5: preview clip (large videos only — uses ffmpeg.wasm to trim first N seconds)
  let previewStorageKey = null
  let wrappedPreviewDek = null
  if (previewInit && isVideo && isLarge) {
    try {
      onStatus('Generating preview…')
      const previewDek = generateDek()
      const previewBytes = await generatePreviewClip(file, previewDurationSeconds)
      const previewEnvelope = await encryptSymmetric(ALG_AES256GCM_V1, previewDek, previewBytes)
      await putBlob(previewInit.uploadUrl, previewEnvelope)
      previewStorageKey = previewInit.storageKey
      wrappedPreviewDek = await wrapDekUnderMasterKey(previewDek, masterKey)
    } catch (e) {
      console.warn('[heirlooms] preview clip generation failed, skipping:', e)
    }
  }

  // Step 6: encrypted metadata
  const metaJson = await buildEncryptedMetadata(file)
  const metaBytes = new TextEncoder().encode(metaJson)
  const metaEnvelope = await encryptSymmetric(ALG_AES256GCM_V1, contentDek, metaBytes)

  // Step 7: wrap DEKs and confirm
  const wrappedDek = await wrapDekUnderMasterKey(contentDek, masterKey)
  const wrappedThumbDek = await wrapDekUnderMasterKey(thumbDek, masterKey)
  const takenAt = new Date(file.lastModified).toISOString()
  return confirmEncryptedUpload(apiKey, {
    storageKey,
    mimeType: file.type,
    fileSize: contentFileSize,
    envelopeVersion: 1,
    wrappedDekB64: toB64(wrappedDek),
    dekFormat: ALG_MASTER_AES256GCM_V1,
    thumbnailStorageKey,
    wrappedThumbnailDekB64: toB64(wrappedThumbDek),
    thumbnailDekFormat: ALG_MASTER_AES256GCM_V1,
    encryptedMetadataB64: toB64(metaEnvelope),
    encryptedMetadataFormat: ALG_AES256GCM_V1,
    previewStorageKey: previewStorageKey ?? undefined,
    wrappedPreviewDekB64: wrappedPreviewDek ? toB64(wrappedPreviewDek) : undefined,
    previewDekFormat: wrappedPreviewDek ? ALG_MASTER_AES256GCM_V1 : undefined,
    plainChunkSize: usedChunkSize,
    durationSeconds,
    takenAt,
    tags: [],
  })
}

export function GardenPage() {
  const { apiKey } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [plots, setPlots] = useState([])
  const [plotsLoading, setPlotsLoading] = useState(true)
  const [plotsError, setPlotsError] = useState(null)
  const [compostCount, setCompostCount] = useState(0)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [showCompostedMsg] = useState(() => !!location.state?.composted)
  const [plotSavedMsg] = useState(() => location.state?.plotSaved ?? null)
  const [quickTagUpload, setQuickTagUpload] = useState(null)
  const [shareUploadItem, setShareUploadItem] = useState(null)
  const [videoUpload, setVideoUpload] = useState(null)
  const [previewUpload, setPreviewUpload] = useState(null)
  const [confirmCompost, setConfirmCompost] = useState(null)
  const [composting, setComposting] = useState(false)
  const [compostError, setCompostError] = useState(null)
  const [plotRefreshKey, setPlotRefreshKey] = useState(0)
  const [justArrivedExclude, setJustArrivedExclude] = useState(new Set())
  const [uploadStatus, setUploadStatus] = useState('')   // '', 'Encrypting…', 'Uploading…', 'Done'
  const [uploadError, setUploadError] = useState(null)
  const [manageMembersPlot, setManageMembersPlot] = useState(null)
  const [showNewSharedPlot, setShowNewSharedPlot] = useState(false)
  const [sharedPlotName, setSharedPlotName] = useState('')
  const [creatingShared, setCreatingShared] = useState(false)
  const [sharedPlotError, setSharedPlotError] = useState(null)
  const fileInputRef = useRef(null)

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const systemPlot = plots.find((p) => p.is_system_defined)
  const userPlots = plots.filter((p) => !p.is_system_defined)
  const userPlotIds = userPlots.map((p) => p.id)

  // Poll all plot rows every 30 seconds to pick up newly arrived items.
  useEffect(() => {
    const id = setInterval(() => setPlotRefreshKey((k) => k + 1), 30_000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    document.title = 'Garden · Heirlooms'
    apiFetch('/api/plots', apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data) => setPlots(Array.isArray(data) ? data.filter((p) => p.show_in_garden !== false) : []))
      .catch((e) => setPlotsError(e.message))
      .finally(() => setPlotsLoading(false))
  }, [apiKey])

  useEffect(() => {
    apiFetch('/api/content/uploads/composted?limit=200', apiKey)
      .then((r) => r.ok ? r.json() : { items: [] })
      .then((data) => setCompostCount(data.items?.length ?? 0))
      .catch(() => {})
  }, [apiKey])

  useEffect(() => {
    if (showCompostedMsg || plotSavedMsg) window.history.replaceState({}, document.title, location.pathname)
  }, [showCompostedMsg, plotSavedMsg, location.pathname])

  function handleDragEnd(event) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = userPlots.findIndex((p) => p.id === active.id)
    const newIndex = userPlots.findIndex((p) => p.id === over.id)
    const reordered = arrayMove(userPlots, oldIndex, newIndex)
    const updated = reordered.map((p, i) => ({ ...p, sort_order: i }))
    setPlots([...(systemPlot ? [systemPlot] : []), ...updated])
    apiFetch('/api/plots', apiKey, {
      method: 'PATCH',
      body: JSON.stringify(updated.map((p, i) => ({ id: p.id, sort_order: i }))),
    }).catch(() => {})
  }

  function handleMoveUp(plotId) {
    const idx = userPlots.findIndex((p) => p.id === plotId)
    if (idx <= 0) return
    const reordered = arrayMove(userPlots, idx, idx - 1)
    const updated = reordered.map((p, i) => ({ ...p, sort_order: i }))
    setPlots([...(systemPlot ? [systemPlot] : []), ...updated])
    apiFetch('/api/plots', apiKey, {
      method: 'PATCH',
      body: JSON.stringify(updated.map((p, i) => ({ id: p.id, sort_order: i }))),
    }).catch(() => {})
  }

  function handleMoveDown(plotId) {
    const idx = userPlots.findIndex((p) => p.id === plotId)
    if (idx < 0 || idx >= userPlots.length - 1) return
    const reordered = arrayMove(userPlots, idx, idx + 1)
    const updated = reordered.map((p, i) => ({ ...p, sort_order: i }))
    setPlots([...(systemPlot ? [systemPlot] : []), ...updated])
    apiFetch('/api/plots', apiKey, {
      method: 'PATCH',
      body: JSON.stringify(updated.map((p, i) => ({ id: p.id, sort_order: i }))),
    }).catch(() => {})
  }

  async function handleQuickUpdateTags(uploadId, tags) {
    const r = await apiFetch(`/api/content/uploads/${uploadId}/tags`, apiKey, {
      method: 'PATCH',
      body: JSON.stringify({ tags }),
    })
    if (!r.ok) {
      let msg = `HTTP ${r.status}`
      try {
        const body = await r.json()
        if (body.tag && body.reason) msg = `"${body.tag}": ${body.reason}`
        else if (body.error) msg = body.error
      } catch {}
      throw new Error(msg)
    }
    // Optimistically remove from Just arrived immediately (it now has tags).
    setJustArrivedExclude((prev) => new Set([...prev, uploadId]))
    setQuickTagUpload(null)
    // Silently re-fetch user plots in the background (no loading flash).
    setPlotRefreshKey((k) => k + 1)
  }

  async function handleCompostConfirmed() {
    setComposting(true)
    setCompostError(null)
    try {
      const r = await apiFetch(`/api/content/uploads/${confirmCompost.id}/compost`, apiKey, { method: 'POST' })
      if (!r.ok) throw new Error('failed')
      setConfirmCompost(null)
      setPlotRefreshKey((k) => k + 1)
    } catch {
      setCompostError("Couldn't compost — it may still have tags or be in an active capsule.")
    } finally {
      setComposting(false)
    }
  }

  async function handlePlant(e) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (!files.length) return
    setUploadError(null)
    for (const file of files) {
      try {
        await encryptAndUpload(file, apiKey, setUploadStatus)
        setUploadStatus('Done')
        setTimeout(() => setUploadStatus(''), 1500)
        setPlotRefreshKey((k) => k + 1)
      } catch (err) {
        setUploadError(`Couldn't upload "${file.name}".`)
        setUploadStatus('')
        setTimeout(() => setUploadError(null), 4000)
      }
    }
  }

  async function handleCreateSharedPlot() {
    if (!sharedPlotName.trim()) return
    setCreatingShared(true); setSharedPlotError(null)
    try {
      const sharingPrivkey = getSharingPrivkey()
      if (!sharingPrivkey) throw new Error('Sharing key not loaded — try logging in again')

      // Generate plot key
      const plotKeyBytes = generatePlotKey()

      // Fetch own sharing pubkey to wrap the plot key for ourselves
      const spkResp = await apiFetch('/api/keys/sharing/me', apiKey)
      if (!spkResp.ok) throw new Error('Could not fetch own sharing pubkey')
      const { pubkey: ownPubkeyB64 } = await spkResp.json()
      const ownPubkey = fromB64(ownPubkeyB64)

      const { wrappedKey, format } = await wrapPlotKeyForMember(plotKeyBytes, ownPubkey)

      const r = await apiFetch('/api/plots', apiKey, {
        method: 'POST',
        body: JSON.stringify({
          name: sharedPlotName.trim(),
          visibility: 'shared',
          wrappedPlotKey: toB64(wrappedKey),
          plotKeyFormat: format,
        }),
      })
      if (!r.ok) {
        const msg = await r.text()
        throw new Error(msg || `HTTP ${r.status}`)
      }
      const newPlot = await r.json()
      setPlots((prev) => [...prev, newPlot])
      setShowNewSharedPlot(false)
      setSharedPlotName('')
    } catch (e) {
      setSharedPlotError(e.message)
    } finally {
      setCreatingShared(false)
    }
  }

  async function handleDeletePlot(plot) {
    try {
      const r = await apiFetch(`/api/plots/${plot.id}`, apiKey, { method: 'DELETE' })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      setPlots((prev) => prev.filter((p) => p.id !== plot.id))
      setConfirmDelete(null)
    } catch (e) {
      alert(`Couldn't delete plot: ${e.message}`)
    }
  }

  if (plotsLoading) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-8">
        <div className="flex justify-center py-20"><WorkingDots size="lg" label="Loading…" /></div>
      </main>
    )
  }

  if (plotsError) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-8">
        <p className="text-center text-earth font-serif italic py-20">
          Something went wrong — {plotsError}
        </p>
      </main>
    )
  }

  return (
    <main className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div className="space-y-1">
          {showCompostedMsg && (
            <p className="font-serif italic text-forest text-sm">Composted. Find it in the compost heap below.</p>
          )}
          {plotSavedMsg && (
            <p className="font-serif italic text-forest text-sm">"{plotSavedMsg}" saved as a plot.</p>
          )}
        </div>
        <div className="flex items-center gap-3 flex-shrink-0">
          {uploadStatus && (
            <span className="text-xs text-text-muted font-serif italic">{uploadStatus}</span>
          )}
          {uploadError && (
            <span className="text-xs text-earth font-serif italic">{uploadError}</span>
          )}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*,video/*"
            multiple
            className="hidden"
            onChange={handlePlant}
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={!!uploadStatus && uploadStatus !== 'Done'}
            className="text-sm font-sans text-parchment bg-forest border border-forest rounded-button px-3 py-1.5 hover:opacity-90 transition-opacity disabled:opacity-40"
          >
            Plant
          </button>
        </div>
      </div>
      <div className="space-y-8">
        {systemPlot && (
          <SystemPlotRow plot={systemPlot} apiKey={apiKey}
            onTagClick={setQuickTagUpload} onVideoPlay={setVideoUpload} onImagePreview={setPreviewUpload} onCompostClick={setConfirmCompost} onShareClick={setShareUploadItem}
            refreshKey={plotRefreshKey} excludeIds={justArrivedExclude} />
        )}

        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={userPlotIds} strategy={verticalListSortingStrategy}>
            <div className="space-y-8">
              {userPlots.map((plot, idx) => (
                <SortablePlotRow
                  key={plot.id}
                  plot={plot}
                  isFirst={idx === 0}
                  isLast={idx === userPlots.length - 1}
                  apiKey={apiKey}
                  onEdit={() => navigate(`/explore?edit_plot=${plot.id}`, { state: { plot } })}
                  onDelete={() => setConfirmDelete(plot)}
                  onMoveUp={() => handleMoveUp(plot.id)}
                  onMoveDown={() => handleMoveDown(plot.id)}
                  onManageMembers={() => setManageMembersPlot(plot)}
                  onTagClick={setQuickTagUpload}
                  onVideoPlay={setVideoUpload}
                  onImagePreview={setPreviewUpload}
                  onCompostClick={setConfirmCompost}
                  onShareClick={setShareUploadItem}
                  refreshKey={plotRefreshKey}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>

        <div className="flex gap-2 flex-wrap">
          <button
            onClick={() => navigate('/explore?new_plot=true')}
            className="text-sm font-sans text-forest border border-forest-25 rounded-button px-3 py-1.5 hover:bg-forest-04 transition-colors"
          >
            + Add a plot
          </button>
          <button
            onClick={() => setShowNewSharedPlot(true)}
            className="text-sm font-sans text-forest border border-forest-25 rounded-button px-3 py-1.5 hover:bg-forest-04 transition-colors"
          >
            + Shared plot
          </button>
        </div>
      </div>

      <div className="mt-12 flex flex-col gap-2">
        <Link to="/flows"
          className="text-sm font-sans text-text-muted hover:text-forest transition-colors">
          Flows
        </Link>
        <Link
          to="/compost"
          className={`text-sm font-sans hover:text-forest transition-colors ${compostCount === 0 ? 'text-text-muted opacity-60' : 'text-text-muted'}`}
        >
          Compost heap ({compostCount})
        </Link>
      </div>

      {confirmDelete && (
        <ConfirmDialog
          title="Delete plot?"
          body={`"${confirmDelete.name}" will be removed. Your photos are not affected.`}
          primaryLabel="Delete"
          primaryClass="bg-earth text-parchment"
          cancelLabel="Keep it"
          onConfirm={() => handleDeletePlot(confirmDelete)}
          onCancel={() => setConfirmDelete(null)}
        />
      )}

      {quickTagUpload && (
        <QuickTagModal
          upload={quickTagUpload}
          apiKey={apiKey}
          onSave={handleQuickUpdateTags}
          onClose={() => setQuickTagUpload(null)}
        />
      )}

      {shareUploadItem && (
        <ShareModal
          upload={shareUploadItem}
          apiKey={apiKey}
          onSuccess={() => setShareUploadItem(null)}
          onCancel={() => setShareUploadItem(null)}
        />
      )}

      {videoUpload && (
        <QuickVideoModal
          upload={videoUpload}
          apiKey={apiKey}
          onClose={() => setVideoUpload(null)}
        />
      )}

      {previewUpload && (
        <QuickImageModal
          upload={previewUpload}
          apiKey={apiKey}
          onClose={() => setPreviewUpload(null)}
        />
      )}

      {confirmCompost && (
        <ConfirmDialog
          title="Compost this item?"
          body="It will be moved to the compost heap and permanently deleted after 90 days."
          primaryLabel={composting ? '…' : 'Compost'}
          primaryClass="bg-earth text-parchment"
          cancelLabel="Keep it"
          onConfirm={handleCompostConfirmed}
          onCancel={() => { setConfirmCompost(null); setCompostError(null) }}
        >
          {compostError && <p className="text-xs text-earth -mt-1">{compostError}</p>}
        </ConfirmDialog>
      )}

      {showNewSharedPlot && (
        <BrandModal onClose={() => { setShowNewSharedPlot(false); setSharedPlotName('') }} width="max-w-sm">
          <h2 className="font-serif italic text-forest text-lg mb-4">New shared plot</h2>
          <p className="text-sm text-text-muted font-sans mb-3">
            A shared plot has a per-plot encryption key. Invite members after creation — they'll each get a wrapped copy of the key.
          </p>
          <div className="space-y-3">
            <input type="text" value={sharedPlotName} onChange={(e) => setSharedPlotName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreateSharedPlot()}
              placeholder="Plot name…" maxLength={100} autoFocus
              className="w-full px-3 py-1.5 border border-forest-25 rounded text-forest bg-parchment text-sm focus:outline-none focus:border-forest"
            />
            {sharedPlotError && <p className="text-earth text-xs">{sharedPlotError}</p>}
            <div className="flex gap-2 justify-end">
              <button onClick={() => { setShowNewSharedPlot(false); setSharedPlotName('') }}
                className="px-3 py-1.5 text-text-muted hover:text-forest text-sm transition-colors">
                Cancel
              </button>
              <button onClick={handleCreateSharedPlot} disabled={creatingShared || !sharedPlotName.trim()}
                className="px-4 py-1.5 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
                {creatingShared ? '…' : 'Create'}
              </button>
            </div>
          </div>
        </BrandModal>
      )}

      {manageMembersPlot && (
        <InviteMemberModal
          plotId={manageMembersPlot.id}
          apiKey={apiKey}
          onClose={() => setManageMembersPlot(null)}
          onMemberAdded={() => setPlotRefreshKey((k) => k + 1)}
        />
      )}
    </main>
  )
}
