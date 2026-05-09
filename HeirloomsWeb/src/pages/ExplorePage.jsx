import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { apiFetch, API_URL } from '../api'
import { PhotoGrid } from '../components/PhotoGrid'
import { WorkingDots } from '../brand/WorkingDots'
import { getThumb } from '../thumbCache'

// ---- Multi-tag picker for filter chrome ------------------------------------

function TagChromePicker({ selected, onChange }) {
  const { apiKey } = useAuth()
  const [allTags, setAllTags] = useState([])
  const [input, setInput] = useState('')
  const [open, setOpen] = useState(false)
  const inputRef = useRef(null)
  const suppressBlurRef = useRef(false)

  useEffect(() => {
    apiFetch('/api/content/uploads/tags', apiKey)
      .then((r) => r.ok ? r.json() : [])
      .then((data) => setAllTags(Array.isArray(data) ? data : []))
      .catch(() => {})
  }, [apiKey])

  const suggestions = allTags
    .filter((t) => !selected.includes(t))
    .filter((t) => !input || t.toLowerCase().startsWith(input.toLowerCase()))

  function addTag(tag) {
    const t = tag.trim().toLowerCase().replace(/\s+/g, '-')
    if (t && !selected.includes(t)) onChange([...selected, t])
    setInput('')
    inputRef.current?.focus()
  }

  function removeTag(tag) { onChange(selected.filter((t) => t !== tag)) }

  function handleKeyDown(e) {
    if (e.key === 'Enter') { e.preventDefault(); if (input.trim()) addTag(input.trim()) }
    else if (e.key === 'Backspace' && !input && selected.length > 0) removeTag(selected[selected.length - 1])
    else if (e.key === 'Escape') setOpen(false)
  }

  return (
    <div className="relative min-w-[180px]">
      <div className="flex flex-wrap gap-1 items-center border border-forest-15 rounded px-1.5 py-0.5 bg-transparent focus-within:ring-1 focus-within:ring-forest-25 cursor-text"
        onClick={() => inputRef.current?.focus()}>
        {selected.map((tag) => (
          <span key={tag} className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-forest-08 text-forest text-[11px]">
            {tag}
            <button type="button"
              onMouseDown={(e) => { e.preventDefault(); removeTag(tag) }}
              className="text-text-muted hover:text-forest leading-none ml-0.5">×</button>
          </span>
        ))}
        <input
          ref={inputRef}
          value={input}
          onChange={(e) => { setInput(e.target.value); setOpen(true) }}
          onFocus={() => setOpen(true)}
          onBlur={() => { if (!suppressBlurRef.current) setOpen(false) }}
          onKeyDown={handleKeyDown}
          placeholder={selected.length ? '' : 'filter by tag…'}
          autoComplete="off"
          className="flex-1 min-w-[80px] text-xs outline-none bg-transparent py-0.5"
        />
      </div>
      {open && (suggestions.length > 0) && (
        <div className="absolute top-full left-0 right-0 mt-0.5 z-10 border border-forest-15 rounded bg-white shadow-md max-h-40 overflow-y-auto"
          onMouseDown={() => { suppressBlurRef.current = true }}
          onMouseUp={() => { suppressBlurRef.current = false }}>
          {suggestions.map((tag) => (
            <button key={tag} type="button" onClick={() => { addTag(tag); setOpen(false) }}
              className="w-full text-left text-xs px-2 py-1.5 hover:bg-forest-04 text-forest">{tag}</button>
          ))}
        </div>
      )}
    </div>
  )
}

function FilterIcon() {
  return (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M3 4h18M7 12h10M11 20h2" />
    </svg>
  )
}

// Three-state segmented control: null=any, true=yes, false=no
function SegmentedControl({ value, onChange, anyLabel = 'Any', yesLabel = 'Yes', noLabel = 'No' }) {
  const btn = (v, label) => (
    <button
      type="button"
      onClick={() => onChange(value === v ? null : v)}
      className={`px-2 py-0.5 text-xs rounded transition-colors ${
        value === v
          ? 'bg-forest text-parchment'
          : 'text-text-muted hover:text-forest hover:bg-forest-04'
      }`}
    >
      {label}
    </button>
  )
  return (
    <div className="inline-flex border border-forest-15 rounded overflow-hidden">
      {btn(null, anyLabel)}
      {btn(true, yesLabel)}
      {btn(false, noLabel)}
    </div>
  )
}

// ---- Filter chrome ---------------------------------------------------------

function FilterChrome({ tags, setTags, fromDate, setFromDate, toDate, setToDate,
  inCapsule, setInCapsule, includeComposted, setIncludeComposted,
  hasLocation, setHasLocation, sort, setSort, onReset, hasActiveFilters }) {

  const [collapsed, setCollapsed] = useState(false)

  // Narrow viewport: collapsed by default on first render if width < 768
  const initialised = useRef(false)
  useEffect(() => {
    if (!initialised.current) {
      initialised.current = true
      setCollapsed(window.innerWidth < 768)
    }
  }, [])

  return (
    <div className="mb-6 border border-forest-08 rounded-card bg-parchment">
      {/* Header row — always visible */}
      <div className="flex items-center gap-3 px-4 py-2.5">
        <button
          type="button"
          onClick={() => setCollapsed((c) => !c)}
          className="flex items-center gap-2 text-sm font-sans text-forest hover:opacity-80 transition-opacity md:hidden"
        >
          <FilterIcon />
          Filters
          {hasActiveFilters && <span className="w-1.5 h-1.5 rounded-full bg-bloom inline-block" />}
        </button>

        {/* Sort dropdown — always visible regardless of collapse */}
        <div className="ml-auto flex items-center gap-2">
          {hasActiveFilters && (
            <button type="button" onClick={onReset}
              className="text-xs text-text-muted hover:text-forest transition-colors">
              Clear filters
            </button>
          )}
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value)}
            className="text-xs border border-forest-15 rounded px-2 py-1 bg-transparent text-forest focus:outline-none focus:ring-1 focus:ring-forest-25"
          >
            <option value="upload_newest">Newest first (uploaded)</option>
            <option value="upload_oldest">Oldest first (uploaded)</option>
            <option value="taken_newest">Newest first (taken)</option>
            <option value="taken_oldest">Oldest first (taken)</option>
          </select>
        </div>
      </div>

      {/* Filter controls — hidden on mobile when collapsed */}
      <div className={`${collapsed ? 'hidden md:flex' : 'flex'} flex-wrap gap-x-6 gap-y-3 px-4 pb-3`}>
        {/* Tags (multi-select with dropdown) */}
        <div className="flex flex-col gap-0.5">
          <label className="text-[10px] text-text-muted uppercase tracking-wide">Tags</label>
          <TagChromePicker selected={tags} onChange={setTags} />
        </div>

        {/* Date range */}
        <div className="flex flex-col gap-0.5">
          <label className="text-[10px] text-text-muted uppercase tracking-wide">Uploaded from</label>
          <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
            className="text-xs border border-forest-15 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="text-[10px] text-text-muted uppercase tracking-wide">Uploaded to</label>
          <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
            className="text-xs border border-forest-15 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent" />
        </div>

        {/* Capsule membership */}
        <div className="flex flex-col gap-0.5">
          <label className="text-[10px] text-text-muted uppercase tracking-wide">Capsule</label>
          <SegmentedControl
            value={inCapsule} onChange={setInCapsule}
            anyLabel="Any" yesLabel="In capsule" noLabel="No capsule"
          />
        </div>

        {/* Location */}
        <div className="flex flex-col gap-0.5">
          <label className="text-[10px] text-text-muted uppercase tracking-wide">Location</label>
          <SegmentedControl
            value={hasLocation} onChange={setHasLocation}
            anyLabel="Any" yesLabel="Has location" noLabel="No location"
          />
        </div>

        {/* Composted toggle */}
        <div className="flex flex-col gap-0.5 justify-end">
          <label className="flex items-center gap-1.5 text-xs text-text-muted cursor-pointer select-none">
            <input type="checkbox" checked={includeComposted} onChange={(e) => setIncludeComposted(e.target.checked)}
              className="rounded" />
            Show composted
          </label>
        </div>
      </div>
    </div>
  )
}

// ---- Taken-date sort indicator on individual items -------------------------

function NoMetadataTag() {
  return (
    <span className="absolute bottom-1 left-1 text-[9px] italic text-white bg-black/50 px-1 rounded leading-tight">
      no date
    </span>
  )
}

// Wrapper around PhotoGrid that adds taken-date sort overlays
function ExploreGrid({ uploads, sort, includeComposted }) {
  const showNoDate = sort === 'taken_newest' || sort === 'taken_oldest'
  const showCompostedStyle = includeComposted

  return (
    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-2">
      {uploads.map((upload) => {
        const isComposted = showCompostedStyle && !!upload.compostedAt
        return (
          <Link
            key={upload.id}
            to={`/photos/${upload.id}?from=explore`}
            state={{ upload }}
            className="relative aspect-square overflow-hidden rounded border border-forest-08 block hover:opacity-90 transition-opacity"
          >
            <ExploreThumb upload={upload} isComposted={isComposted} />
            {showNoDate && !upload.capturedAt && <NoMetadataTag />}
          </Link>
        )
      })}
    </div>
  )
}

function ExploreThumb({ upload, isComposted }) {
  const { apiKey } = useAuth()
  const isImage = upload.mimeType?.startsWith('image/')
  const isVideo = upload.mimeType?.startsWith('video/')
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : (isImage ? `${API_URL}/api/content/uploads/${upload.id}/file` : null)

  const [blobUrl, setBlobUrl] = useState(null)

  useEffect(() => {
    if (!displayUrl) return
    let cancelled = false
    let ownUrl = null
    getThumb(upload.id, displayUrl, apiKey)
      .then((url) => {
        if (!cancelled) { ownUrl = url; setBlobUrl(url) }
        else URL.revokeObjectURL(url)
      })
      .catch(() => {})
    return () => { cancelled = true; if (ownUrl) URL.revokeObjectURL(ownUrl) }
  }, [upload.id, displayUrl, apiKey])

  const rotate = upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : {}
  const saturate = isComposted ? { filter: 'saturate(0.35) opacity(0.75)' } : {}

  return (
    <>
      {blobUrl ? (
        <img src={blobUrl} alt="" className="w-full h-full object-cover" style={{ ...rotate, ...saturate }} />
      ) : (
        <div className="w-full h-full bg-forest-08 flex items-center justify-center">
          <span className="text-text-muted text-xs">…</span>
        </div>
      )}
      {isVideo && (
        <div className="absolute bottom-0 right-0 bg-forest-75 rounded-tl p-0.5 pointer-events-none">
          <svg className="w-3.5 h-3.5 text-white" fill="currentColor" viewBox="0 0 24 24">
            <path d="M8 5v14l11-7z" />
          </svg>
        </div>
      )}
    </>
  )
}

// ---- Main page -------------------------------------------------------------

export function ExplorePage() {
  const { apiKey } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  // Plot edit/create mode
  const editPlotId = searchParams.get('edit_plot') ?? null
  const newPlotMode = searchParams.get('new_plot') === 'true'
  const [editPlot, setEditPlot] = useState(null) // {id, name} when editing existing plot
  const [showSaveForm, setShowSaveForm] = useState(false)
  const [saveName, setSaveName] = useState('')
  const [saveError, setSaveError] = useState(null)
  const [saving, setSaving] = useState(false)
  const saveNameRef = useRef(null)

  // Filters
  const [tags, setTags] = useState([])
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [inCapsule, setInCapsule] = useState(null)
  const [includeComposted, setIncludeComposted] = useState(false)
  const [hasLocation, setHasLocation] = useState(null)
  const [sort, setSort] = useState('upload_newest')

  // Items
  const [items, setItems] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState(null)

  // Load plot data when entering edit mode
  useEffect(() => {
    if (!editPlotId) { setEditPlot(null); return }
    const fromState = location.state?.plot
    if (fromState?.id === editPlotId) {
      setEditPlot({ id: fromState.id, name: fromState.name })
      setTags(fromState.tag_criteria ?? [])
      return
    }
    apiFetch('/api/plots', apiKey)
      .then((r) => r.ok ? r.json() : [])
      .then((plots) => {
        const p = Array.isArray(plots) ? plots.find((x) => x.id === editPlotId) : null
        if (p) { setEditPlot({ id: p.id, name: p.name }); setTags(p.tag_criteria ?? []) }
      })
      .catch(() => {})
  }, [editPlotId, apiKey])

  useEffect(() => {
    if (showSaveForm) saveNameRef.current?.focus()
  }, [showSaveForm])

  async function handleSavePlot() {
    if (!saveName.trim()) return
    setSaving(true); setSaveError(null)
    try {
      const r = await apiFetch('/api/plots', apiKey, {
        method: 'POST',
        body: JSON.stringify({ name: saveName.trim(), tag_criteria: tags }),
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      navigate('/', { state: { plotSaved: saveName.trim() } })
    } catch (e) { setSaveError(e.message) } finally { setSaving(false) }
  }

  async function handleUpdatePlot() {
    if (!editPlot) return
    setSaving(true); setSaveError(null)
    try {
      const r = await apiFetch(`/api/plots/${editPlot.id}`, apiKey, {
        method: 'PUT',
        body: JSON.stringify({ tag_criteria: tags }),
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      navigate('/', { state: { plotSaved: editPlot.name } })
    } catch (e) { setSaveError(e.message) } finally { setSaving(false) }
  }

  const hasActiveFilters = !!(tags.length > 0 || fromDate || toDate || inCapsule !== null ||
    includeComposted || hasLocation !== null || sort !== 'upload_newest')

  const buildUrl = useCallback((cursor) => {
    const params = new URLSearchParams({ limit: '50' })
    if (tags.length > 0) params.set('tag', tags.join(','))
    if (fromDate) params.set('from_date', fromDate)
    if (toDate) params.set('to_date', toDate)
    if (inCapsule === true) params.set('in_capsule', 'true')
    else if (inCapsule === false) params.set('in_capsule', 'false')
    if (includeComposted) params.set('include_composted', 'true')
    if (hasLocation === true) params.set('has_location', 'true')
    else if (hasLocation === false) params.set('has_location', 'false')
    if (sort !== 'upload_newest') params.set('sort', sort)
    if (cursor) params.set('cursor', cursor)
    return `/api/content/uploads?${params}`
  }, [tags.join(','), fromDate, toDate, inCapsule, includeComposted, hasLocation, sort])

  useEffect(() => {
    document.title = 'Explore · Heirlooms'
  }, [])

  useEffect(() => {
    setLoading(true)
    setError(null)
    setItems([])
    setNextCursor(null)
    apiFetch(buildUrl(null), apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data) => { setItems(data.items ?? []); setNextCursor(data.next_cursor ?? null) })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [buildUrl, apiKey])

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) return
    setLoadingMore(true)
    try {
      const data = await apiFetch(buildUrl(nextCursor), apiKey).then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      setItems((prev) => [...prev, ...(data.items ?? [])])
      setNextCursor(data.next_cursor ?? null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingMore(false)
    }
  }

  function handleReset() {
    setTags([]); setFromDate(''); setToDate('')
    setInCapsule(null); setIncludeComposted(false)
    setHasLocation(null); setSort('upload_newest')
  }

  return (
    <main className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="font-serif italic text-forest text-xl mb-6">Explore</h1>

      {/* Edit-mode banner */}
      {editPlot && (
        <div className="mb-4 flex items-center gap-3 px-4 py-2.5 bg-forest-08 border border-forest-25 rounded-card text-sm">
          <span className="text-forest font-sans flex-1">
            Editing <span className="font-medium">"{editPlot.name}"</span>
            {tags.length > 0 ? <span className="text-text-muted"> — tags: {tags.join(', ')}</span> : <span className="text-text-muted"> — no tags set</span>}
          </span>
          {saveError && <span className="text-earth text-xs">{saveError}</span>}
          <button onClick={handleUpdatePlot} disabled={saving}
            className="px-3 py-1 bg-forest text-parchment rounded-button text-xs hover:opacity-90 transition-opacity disabled:opacity-40">
            {saving ? '…' : 'Update plot'}
          </button>
          <button onClick={() => navigate('/')}
            className="px-3 py-1 text-text-muted hover:text-forest transition-colors text-xs">
            Cancel
          </button>
        </div>
      )}

      {/* New-plot hint */}
      {newPlotMode && !editPlot && (
        <div className="mb-4 px-4 py-2.5 bg-forest-04 border border-forest-15 rounded-card text-sm text-text-muted font-sans">
          Apply a <span className="font-medium text-forest">Tag</span> filter below, then click <span className="font-medium text-forest">Save as plot</span> when the results look right.
        </div>
      )}

      <FilterChrome
        tags={tags} setTags={setTags}
        fromDate={fromDate} setFromDate={setFromDate}
        toDate={toDate} setToDate={setToDate}
        inCapsule={inCapsule} setInCapsule={setInCapsule}
        includeComposted={includeComposted} setIncludeComposted={setIncludeComposted}
        hasLocation={hasLocation} setHasLocation={setHasLocation}
        sort={sort} setSort={setSort}
        onReset={handleReset}
        hasActiveFilters={hasActiveFilters}
      />

      {/* Save-as-plot bar (not in edit mode, tag filter active) */}
      {!editPlot && tags.length > 0 && !showSaveForm && (
        <div className="mb-4 flex items-center gap-3 px-4 py-2 bg-forest-04 border border-forest-15 rounded-card text-sm">
          <span className="text-text-muted font-sans flex-1">
            {tags.length === 1
              ? <span>Tag: <span className="text-forest font-medium">{tags[0]}</span></span>
              : <span>Tags: {tags.map((t, i) => <span key={t}><span className="text-forest font-medium">{t}</span>{i < tags.length - 1 ? ', ' : ''}</span>)}</span>
            }
          </span>
          <button onClick={() => setShowSaveForm(true)}
            className="px-3 py-1 text-forest border border-forest-25 rounded-button text-xs hover:bg-forest-08 transition-colors">
            Save as plot…
          </button>
        </div>
      )}
      {showSaveForm && !editPlot && (
        <div className="mb-4 flex items-center gap-2 px-4 py-2.5 bg-forest-04 border border-forest-15 rounded-card">
          <input
            ref={saveNameRef}
            value={saveName}
            onChange={(e) => setSaveName(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSavePlot(); if (e.key === 'Escape') setShowSaveForm(false) }}
            placeholder="Plot name…"
            maxLength={100}
            className="flex-1 text-sm border border-forest-15 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-white"
          />
          {saveError && <span className="text-earth text-xs">{saveError}</span>}
          <button onClick={handleSavePlot} disabled={saving || !saveName.trim()}
            className="px-3 py-1 bg-forest text-parchment rounded-button text-sm hover:opacity-90 transition-opacity disabled:opacity-40 flex-shrink-0">
            {saving ? '…' : 'Save'}
          </button>
          <button onClick={() => { setShowSaveForm(false); setSaveName('') }}
            className="px-2 py-1 text-text-muted hover:text-forest text-sm transition-colors flex-shrink-0">
            Cancel
          </button>
        </div>
      )}

      {loading && (
        <div className="flex justify-center py-20">
          <WorkingDots size="lg" label="Loading…" />
        </div>
      )}

      {error && (
        <p className="text-center text-earth font-serif italic py-20">
          Something went wrong — {error}
        </p>
      )}

      {!loading && !error && items.length === 0 && (
        <p className="text-text-muted text-sm font-sans text-center py-16">
          {hasActiveFilters ? 'No items match the current filters.' : 'Nothing here yet.'}
        </p>
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <ExploreGrid uploads={items} sort={sort} includeComposted={includeComposted} />
          {nextCursor && (
            <div className="mt-8 flex justify-center">
              <button
                onClick={handleLoadMore}
                disabled={loadingMore}
                className="px-4 py-2 text-sm font-sans text-forest border border-forest-25 rounded-button hover:bg-forest-04 transition-colors disabled:opacity-40"
              >
                {loadingMore ? <WorkingDots size="sm" /> : 'Load more'}
              </button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
