import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { API_URL, apiFetch } from '../api'
import { WorkingDots } from '../brand/WorkingDots'
import { EmptyGarden } from '../brand/EmptyGarden'
import { OliveBranchArrival } from '../brand/OliveBranchArrival'
import { OliveBranchDidntTake } from '../brand/OliveBranchDidntTake'

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric',
  })
}

function isImage(mimeType) { return mimeType?.startsWith('image/') }
function isVideo(mimeType) { return mimeType?.startsWith('video/') }

function FileIcon() {
  return (
    <svg className="w-16 h-16 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M15.172 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V8.828a2 2 0 00-.586-1.414l-3.828-3.828A2 2 0 0015.172 3z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 3v5a1 1 0 001 1h5" />
    </svg>
  )
}

function VideoIcon() {
  return (
    <svg className="w-16 h-16 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
    </svg>
  )
}

function RefreshIcon({ spinning = false }) {
  return (
    <svg className={`w-4 h-4 ${spinning ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  )
}

function PlayIcon() {
  return (
    <div className="w-12 h-12 rounded-full bg-black/50 flex items-center justify-center">
      <svg className="w-6 h-6 text-white ml-1" fill="currentColor" viewBox="0 0 24 24">
        <path d="M8 5v14l11-7z" />
      </svg>
    </div>
  )
}

function RotateIcon() {
  return (
    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5}
        d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  )
}

function TagIcon() {
  return (
    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A2 2 0 013 12V7a2 2 0 012-2h2z" />
    </svg>
  )
}

function TagEditor({ currentTags, allTags, onSave, onCancel }) {
  const [selected, setSelected] = useState([...currentTags])
  const [input, setInput] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const inputRef = useRef(null)
  const suppressBlurRef = useRef(false)

  useEffect(() => { inputRef.current?.focus() }, [])

  const suggestions = allTags
    .filter((t) => !selected.includes(t))
    .filter((t) => !input || t.startsWith(input.toLowerCase()))

  function addTag(tag) {
    const t = tag.trim().toLowerCase()
    if (t && !selected.includes(t)) setSelected((prev) => [...prev, t])
    setInput('')
    inputRef.current?.focus()
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') { e.preventDefault(); if (input.trim()) addTag(input.trim()) }
    else if (e.key === 'Escape') { if (dropdownOpen) setDropdownOpen(false); else onCancel() }
    else if (e.key === 'Backspace' && !input && selected.length > 0) setSelected((prev) => prev.slice(0, -1))
  }

  async function handleSave() {
    const pending = input.trim().toLowerCase()
    const finalTags = pending && !selected.includes(pending) ? [...selected, pending] : selected
    setSaving(true); setError(null)
    try { await onSave(finalTags) } catch (err) { setError(err.message) } finally { setSaving(false) }
  }

  return (
    <div className="pt-1">
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-1.5">
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
        onChange={(e) => { setInput(e.target.value); setDropdownOpen(true) }}
        onFocus={() => setDropdownOpen(true)}
        onBlur={() => { if (!suppressBlurRef.current) setDropdownOpen(false) }}
        onKeyDown={handleKeyDown}
        placeholder="Add tag…" autoComplete="off" disabled={saving}
        className="w-full text-xs border border-forest-15 rounded px-1.5 py-0.5 focus:outline-none focus:ring-1 focus:ring-forest-25 bg-transparent" />
      {dropdownOpen && suggestions.length > 0 && (
        <div className="mt-0.5 border border-forest-15 rounded bg-white shadow-sm max-h-28 overflow-y-auto"
          onMouseDown={() => { suppressBlurRef.current = true }}
          onMouseUp={() => { suppressBlurRef.current = false }}>
          {suggestions.map((tag) => (
            <button key={tag} type="button" onClick={() => addTag(tag)}
              className="w-full text-left text-xs px-2 py-1 hover:bg-forest-04 text-forest">{tag}</button>
          ))}
        </div>
      )}
      {error && <p className="text-xs text-earth mt-0.5">{error}</p>}
      <div className="flex gap-1 mt-1.5">
        <button type="button" onClick={handleSave} disabled={saving}
          className="text-xs px-2 py-0.5 bg-forest text-parchment rounded-button hover:opacity-90 disabled:opacity-40 transition-opacity">
          {saving ? '…' : 'Save'}</button>
        <button type="button" onClick={onCancel} disabled={saving}
          className="text-xs px-2 py-0.5 text-text-muted hover:text-forest transition-colors">Cancel</button>
      </div>
    </div>
  )
}

function FailedTile({ onRetry, onDismiss }) {
  return (
    <div className="gallery-tile--failed">
      <p className="text-earth text-[12px]">Couldn't upload.</p>
      <div className="flex gap-2 mt-2">
        <button onClick={onRetry}
          className="px-2 py-1 bg-forest text-parchment rounded-button text-[10px]">Try again</button>
        <button onClick={onDismiss}
          className="px-2 py-1 text-text-muted text-[10px]">Dismiss</button>
      </div>
    </div>
  )
}

// Session-level thumbnail cache — blob URLs survive navigation within the same page session.
const thumbnailCache = new Map()

function UploadCard({ upload, apiKey, onRotate, onUpdateTags, allTags, isNew }) {
  const fileUrl = `${API_URL}/api/content/uploads/${upload.id}/file`
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : fileUrl
  const needsFetch = isImage(upload.mimeType) || !!upload.thumbnailKey
  const cached = needsFetch ? (thumbnailCache.get(upload.id) ?? null) : null
  const [tileState, setTileState] = useState(needsFetch ? (cached ? 'arrived' : 'loading') : 'arrived')
  const [blobUrl, setBlobUrl] = useState(cached)
  const [fetchAttempt, setFetchAttempt] = useState(0)
  const [editingTags, setEditingTags] = useState(false)
  const animateArrivalRef = useRef(isNew)

  useEffect(() => {
    if (!needsFetch) return
    // Cache hit on initial load — skip network fetch entirely.
    if (fetchAttempt === 0 && thumbnailCache.has(upload.id)) {
      setBlobUrl(thumbnailCache.get(upload.id))
      setTileState('arrived')
      return
    }
    setTileState('loading')
    let cancelled = false
    fetch(displayUrl, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((blob) => {
        if (cancelled) return
        const url = URL.createObjectURL(blob)
        thumbnailCache.set(upload.id, url)
        setBlobUrl(url)
        const shouldAnimate = animateArrivalRef.current
        animateArrivalRef.current = false
        setTileState(shouldAnimate ? 'arriving' : 'arrived')
      })
      .catch(() => { if (!cancelled) setTileState('error-animating') })
    // Blob URLs are intentionally not revoked on unmount — they live in thumbnailCache
    // so back-navigation is instant. The browser frees them when the session ends.
    return () => { cancelled = true }
  }, [displayUrl, apiKey, fetchAttempt, needsFetch, upload.id])

  function renderTileContent() {
    switch (tileState) {
      case 'loading': return <WorkingDots size="md" />
      case 'arriving': return (
        <div className="gallery-tile--animating">
          <OliveBranchArrival withWordmark={false} onComplete={() => setTileState('arrived')} />
        </div>
      )
      case 'error-animating': return (
        <div className="gallery-tile--animating">
          <OliveBranchDidntTake onComplete={() => setTileState('failed')} />
        </div>
      )
      case 'failed': return (
        <FailedTile onRetry={() => setFetchAttempt((n) => n + 1)} onDismiss={() => setTileState('dismissed')} />
      )
      case 'dismissed': return isVideo(upload.mimeType) ? <VideoIcon /> : <FileIcon />
      default:
        if (isImage(upload.mimeType) && blobUrl) {
          return (
            <Link to={`/photos/${upload.id}`} state={{ upload }} className="block w-full h-full">
              <img src={blobUrl} alt={upload.storageKey}
                className="object-cover w-full h-full hover:opacity-90 transition-opacity"
                style={upload.rotation ? { transform: `rotate(${upload.rotation}deg)`, transition: 'transform 0.2s' } : {}} />
            </Link>
          )
        }
        if (isVideo(upload.mimeType) && blobUrl) {
          return (
            <Link to={`/photos/${upload.id}`} state={{ upload }} className="relative w-full h-full block group">
              <img src={blobUrl} alt={upload.storageKey} className="object-cover w-full h-full" />
              <div className="absolute inset-0 flex items-center justify-center bg-black/20 group-hover:bg-black/40 transition-colors">
                <PlayIcon />
              </div>
            </Link>
          )
        }
        if (isVideo(upload.mimeType)) return <Link to={`/photos/${upload.id}`} state={{ upload }}
          className="flex flex-col items-center gap-2 hover:opacity-70 transition-opacity"><VideoIcon /><span className="text-xs text-text-muted">Click to play</span></Link>
        return <FileIcon />
    }
  }

  const showPhoto = tileState === 'arrived' && blobUrl
  return (
    <div className="relative bg-white rounded-card shadow-sm border border-forest-08 flex flex-col">
      <div className={`h-48 flex items-center justify-center overflow-hidden rounded-t-card ${showPhoto ? 'bg-forest-04' : 'bg-forest-15'}`}>
        {renderTileContent()}
      </div>
      <div className="p-3 space-y-1 text-sm text-text-body">
        <div className="flex items-start justify-between gap-1">
          <Link to={`/photos/${upload.id}`} state={{ upload }}
            className="font-medium text-forest truncate hover:underline" title={upload.storageKey}>
            {upload.storageKey}
          </Link>
          <div className="flex-shrink-0 flex items-center gap-0.5">
            {isImage(upload.mimeType) && onRotate && (
              <button onClick={() => onRotate(upload.id)} title="Rotate 90°"
                className="text-text-muted hover:text-forest transition-colors p-0.5"><RotateIcon /></button>
            )}
            {onUpdateTags && (
              <button onClick={() => setEditingTags(true)} title="Edit tags"
                className={`p-0.5 transition-colors ${editingTags ? 'text-forest' : 'text-text-muted hover:text-forest'}`}>
                <TagIcon /></button>
            )}
          </div>
        </div>
        <p className="text-text-muted text-xs">{upload.mimeType}</p>
        <p className="text-text-muted text-xs">{formatBytes(upload.fileSize)}</p>
        <p className="text-text-muted text-xs">{formatDate(upload.uploadedAt)}</p>
        {editingTags ? (
          <TagEditor currentTags={upload.tags} allTags={allTags}
            onSave={async (tags) => { await onUpdateTags(upload.id, tags); setEditingTags(false) }}
            onCancel={() => setEditingTags(false)} />
        ) : upload.tags.length > 0 ? (
          <div className="flex flex-wrap gap-1 pt-1">
            {upload.tags.map((tag) => (
              <span key={tag} className="inline-flex items-center px-[9px] py-[3px] rounded-chip bg-forest-08 text-forest text-[11px]">{tag}</span>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  )
}

export function GardenPage() {
  const { apiKey } = useAuth()
  const location = useLocation()
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const seenIdsRef = useRef(null)
  const [newUploadIds, setNewUploadIds] = useState(new Set())
  const [compostCount, setCompostCount] = useState(0)
  const [showCompostedMsg] = useState(() => !!location.state?.composted)

  const allTags = useMemo(() => [...new Set(uploads.flatMap((u) => u.tags))].sort(), [uploads])

  const fetchUploads = useCallback(async (showSpinner = false) => {
    if (showSpinner) setRefreshing(true)
    try {
      const r = await apiFetch('/api/content/uploads', apiKey)
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const data = await r.json()
      let newIds = new Set()
      if (seenIdsRef.current === null) {
        seenIdsRef.current = new Set(data.map((u) => u.id))
      } else {
        newIds = new Set(data.filter((u) => !seenIdsRef.current.has(u.id)).map((u) => u.id))
        newIds.forEach((id) => seenIdsRef.current.add(id))
      }
      setUploads(data)
      setNewUploadIds(newIds)
    } catch (e) {
      if (showSpinner) setError(e.message)
    } finally {
      if (showSpinner) setRefreshing(false)
      setLoading(false)
    }
  }, [apiKey])

  useEffect(() => {
    document.title = 'Garden · Heirlooms'
    fetchUploads()
    if (showCompostedMsg) window.history.replaceState({}, document.title, location.pathname)
  }, [fetchUploads])

  useEffect(() => {
    apiFetch('/api/content/uploads/composted', apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject())
      .then((data) => setCompostCount(data.uploads?.length ?? 0))
      .catch(() => {})
  }, [apiKey])

  useEffect(() => {
    if (!autoRefresh) return
    const id = setInterval(() => fetchUploads(), 10_000)
    return () => clearInterval(id)
  }, [autoRefresh, fetchUploads])

  async function handleRotate(uploadId) {
    setUploads((prev) => prev.map((u) => {
      if (u.id !== uploadId) return u
      const newRotation = ((u.rotation ?? 0) + 90) % 360
      apiFetch(`/api/content/uploads/${uploadId}/rotation`, apiKey, {
        method: 'PATCH',
        body: JSON.stringify({ rotation: newRotation }),
      }).catch(() => {})
      return { ...u, rotation: newRotation }
    }))
  }

  async function handleUpdateTags(uploadId, tags) {
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
    const updated = await r.json()
    setUploads((prev) => prev.map((u) => u.id === uploadId ? updated : u))
  }

  return (
    <div>
      <div className="border-b border-forest-08 px-6 py-3 flex items-center justify-between">
        <div />
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-text-muted cursor-pointer select-none">
            <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} className="rounded" />
            Auto-refresh
          </label>
          <button onClick={() => fetchUploads(true)} disabled={refreshing} title="Refresh"
            className="text-text-muted hover:text-forest transition-colors disabled:opacity-40">
            <RefreshIcon spinning={refreshing} />
          </button>
        </div>
      </div>

      <main className="max-w-7xl mx-auto px-4 py-8">
        {showCompostedMsg && (
          <p className="font-serif italic text-forest text-sm mb-6">
            Composted. Find it in the compost heap below.
          </p>
        )}
        {loading && <div className="flex justify-center py-20"><WorkingDots size="lg" label="Loading…" /></div>}
        {error && <p className="text-center text-earth font-serif italic py-20">Something went wrong — {error}</p>}
        {!loading && !error && uploads.length === 0 && <EmptyGarden />}
        {!loading && !error && uploads.length > 0 && (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {uploads.map((upload) => (
              <UploadCard key={upload.id} upload={upload} apiKey={apiKey}
                isNew={newUploadIds.has(upload.id)}
                onRotate={handleRotate} onUpdateTags={handleUpdateTags} allTags={allTags} />
            ))}
          </div>
        )}
        {!loading && (
          <div className="mt-12">
            <Link
              to="/compost"
              className={`text-sm font-sans hover:text-forest transition-colors ${compostCount === 0 ? 'text-text-muted opacity-60' : 'text-text-muted'}`}
            >
              Compost heap ({compostCount})
            </Link>
          </div>
        )}
      </main>
    </div>
  )
}
