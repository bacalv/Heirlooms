import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../AuthContext'
import { apiFetch, API_URL } from '../api'
import { PhotoGrid } from '../components/PhotoGrid'
import { WorkingDots } from '../brand/WorkingDots'

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

function FilterChrome({ tag, setTag, fromDate, setFromDate, toDate, setToDate,
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
        {/* Tag */}
        <div className="flex flex-col gap-0.5 min-w-[140px]">
          <label className="text-[10px] text-text-muted uppercase tracking-wide">Tag</label>
          <input
            type="text"
            value={tag}
            onChange={(e) => setTag(e.target.value.toLowerCase().replace(/\s/g, '-'))}
            placeholder="filter by tag"
            className="text-xs border border-forest-15 rounded px-2 py-1 w-full focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent"
          />
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
          <a
            key={upload.id}
            href={`/photos/${upload.id}?from=explore`}
            className="relative aspect-square overflow-hidden rounded border border-forest-08 block hover:opacity-90 transition-opacity"
          >
            <ExploreThumb upload={upload} isComposted={isComposted} />
            {showNoDate && !upload.capturedAt && <NoMetadataTag />}
          </a>
        )
      })}
    </div>
  )
}

function ExploreThumb({ upload, isComposted }) {
  const { apiKey } = useAuth()
  const isImage = upload.mimeType?.startsWith('image/')
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : (isImage ? `${API_URL}/api/content/uploads/${upload.id}/file` : null)

  const [blobUrl, setBlobUrl] = useState(null)

  useEffect(() => {
    if (!displayUrl) return
    let cancelled = false
    fetch(displayUrl, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject())
      .then((blob) => { if (!cancelled) setBlobUrl(URL.createObjectURL(blob)) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [displayUrl, apiKey])

  const rotate = upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : {}
  const saturate = isComposted ? { filter: 'saturate(0.35) opacity(0.75)' } : {}

  if (blobUrl) {
    return <img src={blobUrl} alt="" className="w-full h-full object-cover" style={{ ...rotate, ...saturate }} />
  }
  return (
    <div className="w-full h-full bg-forest-08 flex items-center justify-center">
      <span className="text-text-muted text-xs">…</span>
    </div>
  )
}

// ---- Main page -------------------------------------------------------------

export function ExplorePage() {
  const { apiKey } = useAuth()

  // Filters
  const [tag, setTag] = useState('')
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

  const hasActiveFilters = !!(tag || fromDate || toDate || inCapsule !== null ||
    includeComposted || hasLocation !== null || sort !== 'upload_newest')

  const buildUrl = useCallback((cursor) => {
    const params = new URLSearchParams({ limit: '50' })
    if (tag) params.set('tag', tag)
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
  }, [tag, fromDate, toDate, inCapsule, includeComposted, hasLocation, sort])

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
    setTag(''); setFromDate(''); setToDate('')
    setInCapsule(null); setIncludeComposted(false)
    setHasLocation(null); setSort('upload_newest')
  }

  return (
    <main className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="font-serif italic text-forest text-xl mb-6">Explore</h1>

      <FilterChrome
        tag={tag} setTag={setTag}
        fromDate={fromDate} setFromDate={setFromDate}
        toDate={toDate} setToDate={setToDate}
        inCapsule={inCapsule} setInCapsule={setInCapsule}
        includeComposted={includeComposted} setIncludeComposted={setIncludeComposted}
        hasLocation={hasLocation} setHasLocation={setHasLocation}
        sort={sort} setSort={setSort}
        onReset={handleReset}
        hasActiveFilters={hasActiveFilters}
      />

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
