import { useCallback, useEffect, useRef, useState } from 'react' // useMemo removed with PlotForm
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BrandModal } from '../components/BrandModal'
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
import { API_URL, apiFetch } from '../api'
import { WorkingDots } from '../brand/WorkingDots'
import { ConfirmDialog } from '../components/ConfirmDialog'

const JUST_ARRIVED_SENTINEL = '__just_arrived__'

// ---- Thumbnail card for horizontal plot row --------------------------------

function PlotThumbCard({ upload, apiKey, onTagClick, onVideoPlay }) {
  const isImage = upload.mimeType?.startsWith('image/')
  const isVideo = upload.mimeType?.startsWith('video/')
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : (isImage ? `${API_URL}/api/content/uploads/${upload.id}/file` : null)

  const [blobUrl, setBlobUrl] = useState(null)
  const isComposted = !!upload.compostedAt

  useEffect(() => {
    if (!displayUrl) return
    let cancelled = false
    fetch(displayUrl, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject())
      .then((blob) => {
        if (!cancelled) setBlobUrl(URL.createObjectURL(blob))
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [displayUrl, apiKey])

  const saturate = isComposted ? { filter: 'saturate(0.4) opacity(0.7)' } : {}
  const rotate = upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : {}

  function stop(e, fn) { e.preventDefault(); e.stopPropagation(); fn() }

  const thumbnailContent = blobUrl ? (
    <>
      <img src={blobUrl} alt="" className="w-full h-full object-cover" style={{ ...rotate, ...saturate }} />
      {isVideo && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/25 pointer-events-none">
          <div className="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center">
            <svg className="w-5 h-5 text-white ml-0.5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </div>
        </div>
      )}
    </>
  ) : isVideo ? (
    <div className="w-full h-full flex items-center justify-center bg-forest-08">
      <svg className="w-10 h-10 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
          d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
      </svg>
    </div>
  ) : (
    <div className="w-full h-full bg-forest-08 flex items-center justify-center">
      <span className="text-text-muted text-xs">…</span>
    </div>
  )

  return (
    <div className="flex-shrink-0 w-40 h-40 relative group">
      {/* Video: clicking the play icon opens modal; clicking elsewhere navigates */}
      {isVideo && onVideoPlay ? (
        <button
          onClick={(e) => stop(e, () => onVideoPlay(upload))}
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
    </div>
  )
}

// ---- Horizontal scrolling row of items for one plot ------------------------

function PlotItemsRow({ plot, apiKey, onTagClick, onVideoPlay, refreshKey, excludeIds }) {
  const [items, setItems] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const rowRef = useRef(null)

  const buildUrl = useCallback((cursor) => {
    const params = new URLSearchParams({ limit: '50' })
    if (plot.name === JUST_ARRIVED_SENTINEL) {
      params.set('just_arrived', 'true')
    } else if (plot.tag_criteria?.length > 0) {
      params.set('tag', plot.tag_criteria.join(','))
    }
    if (cursor) params.set('cursor', cursor)
    return `/api/content/uploads?${params}`
  }, [plot.name, plot.tag_criteria?.join(',')])

  // Full reload (shows loading state) — for initial mount and plot criteria changes
  useEffect(() => {
    setLoading(true)
    setItems([])
    setNextCursor(null)
    apiFetch(buildUrl(null), apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data) => { setItems(data.items ?? []); setNextCursor(data.next_cursor ?? null) })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [buildUrl, apiKey])

  // Silent re-fetch triggered externally (refreshKey) — swaps items without loading flash
  useEffect(() => {
    if (refreshKey === 0) return
    apiFetch(buildUrl(null), apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject())
      .then((data) => { setItems(data.items ?? []); setNextCursor(data.next_cursor ?? null) })
      .catch(() => {})
  }, [refreshKey])

  // Optimistic exclusion: items removed immediately by the parent (e.g. after tagging)
  const visibleItems = excludeIds?.size ? items.filter((u) => !excludeIds.has(u.id)) : items

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
          onTagClick={onTagClick} onVideoPlay={onVideoPlay} />
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

function PlotGearMenu({ plot, isFirst, isLast, onEdit, onDelete, onMoveUp, onMoveDown }) {
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
          <button className="w-full text-left px-3 py-1.5 hover:bg-forest-04 text-forest"
            onClick={() => { setOpen(false); onEdit() }}>Edit</button>
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

// ---- System (Just arrived) plot row — no DnD, no gear ----------------------

function SystemPlotRow({ plot, apiKey, onTagClick, onVideoPlay, refreshKey, excludeIds }) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <h2 className="font-serif italic text-forest text-base">Just arrived</h2>
      </div>
      <PlotItemsRow plot={plot} apiKey={apiKey}
        onTagClick={onTagClick} onVideoPlay={onVideoPlay} refreshKey={refreshKey} excludeIds={excludeIds} />
    </div>
  )
}

// ---- Sortable user plot row ------------------------------------------------

function SortablePlotRow({ plot, isFirst, isLast, apiKey, onEdit, onDelete, onMoveUp, onMoveDown, onTagClick, onVideoPlay, refreshKey, excludeIds }) {
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
        <h2 className="font-sans text-forest text-sm font-medium flex-1">{plot.name}</h2>
        <PlotGearMenu
          plot={plot}
          isFirst={isFirst}
          isLast={isLast}
          onEdit={onEdit}
          onDelete={onDelete}
          onMoveUp={onMoveUp}
          onMoveDown={onMoveDown}
        />
      </div>
      <PlotItemsRow plot={plot} apiKey={apiKey}
        onTagClick={onTagClick} onVideoPlay={onVideoPlay} refreshKey={refreshKey} excludeIds={excludeIds} />
    </div>
  )
}

// ---- Main GardenPage -------------------------------------------------------

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
  const [videoUpload, setVideoUpload] = useState(null)
  const [plotRefreshKey, setPlotRefreshKey] = useState(0)
  const [justArrivedExclude, setJustArrivedExclude] = useState(new Set())

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const systemPlot = plots.find((p) => p.is_system_defined)
  const userPlots = plots.filter((p) => !p.is_system_defined)
  const userPlotIds = userPlots.map((p) => p.id)

  useEffect(() => {
    document.title = 'Garden · Heirlooms'
    apiFetch('/api/plots', apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data) => setPlots(Array.isArray(data) ? data : []))
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

  async function handleDeletePlot(plot) {
    const r = await apiFetch(`/api/plots/${plot.id}`, apiKey, { method: 'DELETE' })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    setPlots((prev) => prev.filter((p) => p.id !== plot.id))
    setConfirmDelete(null)
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
      {showCompostedMsg && (
        <p className="font-serif italic text-forest text-sm mb-6">
          Composted. Find it in the compost heap below.
        </p>
      )}
      {plotSavedMsg && (
        <p className="font-serif italic text-forest text-sm mb-6">
          "{plotSavedMsg}" saved as a plot.
        </p>
      )}
      <div className="space-y-8">
        {systemPlot && (
          <SystemPlotRow plot={systemPlot} apiKey={apiKey}
            onTagClick={setQuickTagUpload} onVideoPlay={setVideoUpload}
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
                  onTagClick={setQuickTagUpload}
                  onVideoPlay={setVideoUpload}
                  refreshKey={plotRefreshKey}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>

        <button
          onClick={() => navigate('/explore?new_plot=true')}
          className="text-sm font-sans text-forest border border-forest-25 rounded-button px-3 py-1.5 hover:bg-forest-04 transition-colors"
        >
          + Add a plot
        </button>
      </div>

      <div className="mt-12">
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

      {videoUpload && (
        <QuickVideoModal
          upload={videoUpload}
          apiKey={apiKey}
          onClose={() => setVideoUpload(null)}
        />
      )}
    </main>
  )
}
