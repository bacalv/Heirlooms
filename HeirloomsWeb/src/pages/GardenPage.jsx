import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
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

function PlotThumbCard({ upload, apiKey }) {
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

  return (
    <Link
      to={`/photos/${upload.id}?from=garden`}
      state={{ upload }}
      className="flex-shrink-0 w-40 h-40 rounded overflow-hidden border border-forest-08 bg-forest-04 block hover:opacity-90 transition-opacity relative"
    >
      {blobUrl ? (
        <img src={blobUrl} alt="" className="w-full h-full object-cover" style={{ ...rotate, ...saturate }} />
      ) : isVideo && !upload.thumbnailKey ? (
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
      )}
    </Link>
  )
}

// ---- Horizontal scrolling row of items for one plot ------------------------

function PlotItemsRow({ plot, apiKey }) {
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

  if (items.length === 0) {
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
      {items.map((upload) => (
        <PlotThumbCard key={upload.id} upload={upload} apiKey={apiKey} />
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

// ---- Plot form (Add / Edit) ------------------------------------------------

function PlotTagPicker({ selected, onChange, suggestions }) {
  const [input, setInput] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const inputRef = useRef(null)
  const suppressBlurRef = useRef(false)

  const filtered = suggestions
    .filter((t) => !selected.includes(t))
    .filter((t) => !input || t.startsWith(input.toLowerCase()))

  function addTag(tag) {
    const t = tag.trim().toLowerCase()
    if (t && !selected.includes(t)) onChange([...selected, t])
    setInput('')
    inputRef.current?.focus()
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') { e.preventDefault(); if (input.trim()) addTag(input.trim()) }
    else if (e.key === 'Backspace' && !input && selected.length > 0) onChange(selected.slice(0, -1))
    else if (e.key === 'Escape') setDropdownOpen(false)
  }

  return (
    <div>
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-1.5">
          {selected.map((tag) => (
            <span key={tag} className="inline-flex items-center gap-1 px-[9px] py-[3px] rounded-chip bg-forest-08 text-forest text-[11px]">
              {tag}
              <button type="button" onMouseDown={(e) => e.preventDefault()}
                onClick={() => onChange(selected.filter((t) => t !== tag))}
                className="text-text-muted text-[13px] leading-none ml-0.5">×</button>
            </span>
          ))}
        </div>
      )}
      <input ref={inputRef} value={input}
        onChange={(e) => { setInput(e.target.value); setDropdownOpen(true) }}
        onFocus={() => setDropdownOpen(true)}
        onBlur={() => { if (!suppressBlurRef.current) setDropdownOpen(false) }}
        onKeyDown={handleKeyDown}
        placeholder="Add tag…" autoComplete="off"
        className="w-full text-xs border border-forest-15 rounded px-1.5 py-0.5 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent" />
      {dropdownOpen && filtered.length > 0 && (
        <div className="mt-0.5 border border-forest-15 rounded bg-white shadow-sm max-h-28 overflow-y-auto"
          onMouseDown={() => { suppressBlurRef.current = true }}
          onMouseUp={() => { suppressBlurRef.current = false }}>
          {filtered.map((tag) => (
            <button key={tag} type="button" onClick={() => addTag(tag)}
              className="w-full text-left text-xs px-2 py-1 hover:bg-forest-04 text-forest">{tag}</button>
          ))}
        </div>
      )}
    </div>
  )
}

function PlotForm({ initial, suggestions, onSave, onCancel }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [tagCriteria, setTagCriteria] = useState(initial?.tag_criteria ?? [])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const nameRef = useRef(null)

  useEffect(() => { nameRef.current?.focus() }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) return
    setSaving(true)
    setError(null)
    try { await onSave(name.trim(), tagCriteria) }
    catch (err) { setError(err.message) }
    finally { setSaving(false) }
  }

  return (
    <form onSubmit={handleSubmit}
      className="border border-forest-15 rounded-card bg-parchment p-4 space-y-3">
      <p className="text-sm font-sans text-forest font-medium">
        {initial ? 'Edit plot' : 'New plot'}
      </p>
      <div>
        <label className="text-xs text-text-muted block mb-0.5">Name</label>
        <input ref={nameRef} value={name} onChange={(e) => setName(e.target.value)}
          maxLength={100} required placeholder="Plot name"
          className="w-full text-sm border border-forest-15 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent" />
      </div>
      <div>
        <label className="text-xs text-text-muted block mb-0.5">Tag criteria</label>
        <PlotTagPicker selected={tagCriteria} onChange={setTagCriteria} suggestions={suggestions} />
        <p className="text-[10px] text-text-muted mt-0.5">Items matching any of these tags appear in this plot.</p>
      </div>
      {error && <p className="text-xs text-earth">{error}</p>}
      <div className="flex gap-2 pt-1">
        <button type="submit" disabled={saving || !name.trim()}
          className="px-3 py-1.5 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40">
          {saving ? '…' : (initial ? 'Save' : 'Create')}
        </button>
        <button type="button" onClick={onCancel} disabled={saving}
          className="px-3 py-1.5 text-text-muted hover:text-forest transition-colors text-sm">
          Cancel
        </button>
      </div>
    </form>
  )
}

// ---- System (Just arrived) plot row — no DnD, no gear ----------------------

function SystemPlotRow({ plot, apiKey }) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <h2 className="font-serif italic text-forest text-base">Just arrived</h2>
      </div>
      <PlotItemsRow plot={plot} apiKey={apiKey} />
    </div>
  )
}

// ---- Sortable user plot row ------------------------------------------------

function SortablePlotRow({ plot, isFirst, isLast, apiKey, onEdit, onDelete, onMoveUp, onMoveDown }) {
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
      <PlotItemsRow plot={plot} apiKey={apiKey} />
    </div>
  )
}

// ---- Main GardenPage -------------------------------------------------------

export function GardenPage() {
  const { apiKey } = useAuth()
  const location = useLocation()
  const [plots, setPlots] = useState([])
  const [plotsLoading, setPlotsLoading] = useState(true)
  const [plotsError, setPlotsError] = useState(null)
  const [compostCount, setCompostCount] = useState(0)
  const [showForm, setShowForm] = useState(false)
  const [editingPlot, setEditingPlot] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [showCompostedMsg] = useState(() => !!location.state?.composted)

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const systemPlot = plots.find((p) => p.is_system_defined)
  const userPlots = plots.filter((p) => !p.is_system_defined)
  const userPlotIds = userPlots.map((p) => p.id)

  // Tags used in existing plot criteria — offered as autocomplete suggestions in the form
  const suggestionTags = useMemo(
    () => [...new Set(plots.flatMap((p) => p.tag_criteria ?? []))].sort(),
    [plots],
  )

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
    if (showCompostedMsg) window.history.replaceState({}, document.title, location.pathname)
  }, [showCompostedMsg, location.pathname])

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

  async function handleAddPlot(name, tagCriteria) {
    const r = await apiFetch('/api/plots', apiKey, {
      method: 'POST',
      body: JSON.stringify({ name, tag_criteria: tagCriteria }),
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const newPlot = await r.json()
    setPlots((prev) => [...prev, newPlot])
    setShowForm(false)
    setEditingPlot(null)
  }

  async function handleEditPlot(name, tagCriteria) {
    const r = await apiFetch(`/api/plots/${editingPlot.id}`, apiKey, {
      method: 'PUT',
      body: JSON.stringify({ name, tag_criteria: tagCriteria }),
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const updated = await r.json()
    setPlots((prev) => prev.map((p) => p.id === editingPlot.id ? updated : p))
    setShowForm(false)
    setEditingPlot(null)
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
      <div className="space-y-8">
        {systemPlot && (
          <SystemPlotRow plot={systemPlot} apiKey={apiKey} />
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
                  onEdit={() => { setEditingPlot(plot); setShowForm(true) }}
                  onDelete={() => setConfirmDelete(plot)}
                  onMoveUp={() => handleMoveUp(plot.id)}
                  onMoveDown={() => handleMoveDown(plot.id)}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>

        {showForm ? (
          <PlotForm
            initial={editingPlot}
            suggestions={suggestionTags}
            onSave={editingPlot ? handleEditPlot : handleAddPlot}
            onCancel={() => { setShowForm(false); setEditingPlot(null) }}
          />
        ) : (
          <button
            onClick={() => { setEditingPlot(null); setShowForm(true) }}
            className="text-sm font-sans text-forest border border-forest-25 rounded-button px-3 py-1.5 hover:bg-forest-04 transition-colors"
          >
            + Add a plot
          </button>
        )}
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
    </main>
  )
}
