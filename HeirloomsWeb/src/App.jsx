import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

const API_URL = import.meta.env.VITE_API_URL ?? ''

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

function isImage(mimeType) {
  return mimeType.startsWith('image/')
}

function isVideo(mimeType) {
  return mimeType.startsWith('video/')
}

function Spinner() {
  return <div className="w-6 h-6 border-2 border-gray-300 border-t-gray-600 rounded-full animate-spin" />
}

function FileIcon() {
  return (
    <svg className="w-16 h-16 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M15.172 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V8.828a2 2 0 00-.586-1.414l-3.828-3.828A2 2 0 0015.172 3z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 3v5a1 1 0 001 1h5" />
    </svg>
  )
}

function VideoIcon() {
  return (
    <svg className="w-16 h-16 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
    </svg>
  )
}

function RefreshIcon({ spinning = false }) {
  return (
    <svg
      className={`w-4 h-4 ${spinning ? 'animate-spin' : ''}`}
      fill="none" stroke="currentColor" viewBox="0 0 24 24"
    >
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

function LoginScreen({ onLogin }) {
  const [value, setValue] = useState('')
  const [error, setError] = useState(null)
  const [checking, setChecking] = useState(false)
  const inputRef = useRef(null)

  useEffect(() => { inputRef.current?.focus() }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    const key = value.trim()
    if (!key) return
    setChecking(true)
    setError(null)
    try {
      const r = await fetch(`${API_URL}/api/content/uploads`, { headers: { 'X-Api-Key': key } })
      if (r.status === 401) setError('Incorrect API key.')
      else if (!r.ok) setError(`Server error (${r.status}).`)
      else onLogin(key)
    } catch {
      setError('Could not reach the server.')
    } finally {
      setChecking(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8 w-full max-w-sm">
        <h1 className="text-xl font-semibold text-gray-900 mb-1">Heirlooms</h1>
        <p className="text-sm text-gray-500 mb-6">Enter your API key to continue.</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            ref={inputRef}
            type="password"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="API key"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-gray-400"
          />
          {error && <p className="text-sm text-red-500">{error}</p>}
          <button
            type="submit"
            disabled={checking || !value.trim()}
            className="w-full bg-gray-900 text-white py-2 rounded-lg text-sm font-medium hover:bg-gray-700 disabled:opacity-40 transition-colors"
          >
            {checking ? 'Checking…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}

function Modal({ onClose, children }) {
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80"
      onClick={onClose}
    >
      <div onClick={(e) => e.stopPropagation()}>
        {children}
      </div>
      <button
        className="absolute top-4 right-6 text-white text-3xl font-light leading-none hover:text-gray-300"
        onClick={onClose}
      >
        &times;
      </button>
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

function Lightbox({ url, rotation = 0, onClose }) {
  const swapped = rotation === 90 || rotation === 270
  return (
    <Modal onClose={onClose}>
      <img
        src={url}
        alt="Full size"
        className="object-contain rounded shadow-2xl"
        style={{
          transform: rotation ? `rotate(${rotation}deg)` : undefined,
          maxWidth: swapped ? '90vh' : '90vw',
          maxHeight: swapped ? '90vw' : '90vh',
          transition: 'transform 0.2s',
        }}
      />
    </Modal>
  )
}

function VideoPlayer({ url, mimeType, onClose }) {
  return (
    <Modal onClose={onClose}>
      <video
        src={url}
        type={mimeType}
        controls
        autoPlay
        className="max-w-[90vw] max-h-[90vh] rounded shadow-2xl bg-black"
      />
    </Modal>
  )
}

function PinIcon({ latitude, longitude }) {
  return (
    <div
      className="absolute top-2 right-2 text-sm leading-none cursor-default select-none"
      title={`${latitude.toFixed(6)}, ${longitude.toFixed(6)}`}
    >
      📍
    </div>
  )
}

function TagEditor({ currentTags, allTags, onSave, onCancel }) {
  const [selected, setSelected] = useState([...currentTags])
  const [input, setInput] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const inputRef = useRef(null)
  // suppressBlurRef: prevents onBlur from closing the dropdown when the user clicks a suggestion.
  // e.preventDefault() on button mousedown and e.relatedTarget are both unreliable in Safari/macOS.
  const suppressBlurRef = useRef(false)

  useEffect(() => { inputRef.current?.focus() }, [])

  const suggestions = allTags
    .filter(t => !selected.includes(t))
    .filter(t => !input || t.startsWith(input.toLowerCase()))

  function addTag(tag) {
    const t = tag.trim().toLowerCase()
    if (t && !selected.includes(t)) setSelected(prev => [...prev, t])
    setInput('')
    inputRef.current?.focus()
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') {
      e.preventDefault()
      if (input.trim()) addTag(input.trim())
    } else if (e.key === 'Escape') {
      if (dropdownOpen) setDropdownOpen(false)
      else onCancel()
    } else if (e.key === 'Backspace' && !input && selected.length > 0) {
      setSelected(prev => prev.slice(0, -1))
    }
  }

  async function handleSave() {
    const pending = input.trim().toLowerCase()
    const finalTags = pending && !selected.includes(pending)
      ? [...selected, pending]
      : selected
    setSaving(true)
    setError(null)
    try {
      await onSave(finalTags)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="pt-1">
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-1.5">
          {selected.map(tag => (
            <span key={tag} className="flex items-center gap-0.5 px-1.5 py-0.5 bg-gray-200 text-gray-700 rounded text-xs">
              {tag}
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => setSelected(prev => prev.filter(t => t !== tag))}
                className="text-gray-400 hover:text-gray-700 leading-none ml-0.5"
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}
      <input
        ref={inputRef}
        value={input}
        onChange={(e) => { setInput(e.target.value); setDropdownOpen(true) }}
        onFocus={() => setDropdownOpen(true)}
        onBlur={() => {
          if (!suppressBlurRef.current) setDropdownOpen(false)
        }}
        onKeyDown={handleKeyDown}
        placeholder="Add tag…"
        autoComplete="off"
        disabled={saving}
        className="w-full text-xs border border-gray-300 rounded px-1.5 py-0.5 focus:outline-none focus:ring-1 focus:ring-gray-400"
      />
      {dropdownOpen && suggestions.length > 0 && (
        <div
          className="mt-0.5 border border-gray-200 rounded bg-white shadow-sm max-h-28 overflow-y-auto"
          onMouseDown={() => { suppressBlurRef.current = true }}
          onMouseUp={() => { suppressBlurRef.current = false }}
        >
          {suggestions.map(tag => (
            <button
              key={tag}
              type="button"
              onClick={() => addTag(tag)}
              className="w-full text-left text-xs px-2 py-1 hover:bg-gray-50 text-gray-700"
            >
              {tag}
            </button>
          ))}
        </div>
      )}
      {error && <p className="text-xs text-red-500 mt-0.5">{error}</p>}
      <div className="flex gap-1 mt-1.5">
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="text-xs px-2 py-0.5 bg-gray-800 text-white rounded hover:bg-gray-600 disabled:opacity-40 transition-colors"
        >
          {saving ? '…' : 'Save'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="text-xs px-2 py-0.5 text-gray-500 hover:text-gray-800 transition-colors"
        >
          Cancel
        </button>
      </div>
    </div>
  )
}

function UploadCard({ upload, apiKey, onImageClick, onVideoClick, onRotate, onUpdateTags, allTags }) {
  const fileUrl = `${API_URL}/api/content/uploads/${upload.id}/file`
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : fileUrl
  const [blobUrl, setBlobUrl] = useState(null)
  const [editingTags, setEditingTags] = useState(false)

  // Pre-fetch image thumbnails on mount so they display in the grid.
  // Videos are fetched on demand when the user clicks, to avoid loading
  // large files until they're actually wanted.
  useEffect(() => {
    if (!isImage(upload.mimeType) && !upload.thumbnailKey) return
    let cancelled = false
    fetch(displayUrl, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : null)
      .then((blob) => { if (!cancelled && blob) setBlobUrl(URL.createObjectURL(blob)) })
      .catch(() => {})
    return () => {
      cancelled = true
      if (blobUrl) URL.revokeObjectURL(blobUrl)
    }
  }, [displayUrl, apiKey])

  function handleVideoClick() {
    onVideoClick({ uploadId: upload.id, mimeType: upload.mimeType, apiKey })
  }

  return (
    <div className="relative bg-white rounded-xl shadow-sm border border-gray-100 flex flex-col">
      <div className="bg-gray-50 h-48 flex items-center justify-center overflow-hidden rounded-t-xl">
        {isImage(upload.mimeType) ? (
          blobUrl ? (
            <img
              src={blobUrl}
              alt={upload.storageKey}
              className="object-cover w-full h-full cursor-pointer hover:opacity-90 transition-opacity"
              style={upload.rotation ? { transform: `rotate(${upload.rotation}deg)`, transition: 'transform 0.2s' } : {}}
              onClick={() => onImageClick(blobUrl, upload.rotation ?? 0)}
            />
          ) : (
            <Spinner />
          )
        ) : isVideo(upload.mimeType) && blobUrl ? (
          <div
            className="relative w-full h-full cursor-pointer group"
            onClick={handleVideoClick}
          >
            <img
              src={blobUrl}
              alt={upload.storageKey}
              className="object-cover w-full h-full"
            />
            <div className="absolute inset-0 flex items-center justify-center bg-black/20 group-hover:bg-black/40 transition-colors">
              <PlayIcon />
            </div>
          </div>
        ) : isVideo(upload.mimeType) ? (
          <button
            className="flex flex-col items-center gap-2 hover:opacity-70 transition-opacity cursor-pointer"
            onClick={handleVideoClick}
          >
            {upload.thumbnailKey ? <Spinner /> : <VideoIcon />}
            {!upload.thumbnailKey && <span className="text-xs text-gray-400">Click to play</span>}
          </button>
        ) : (
          <FileIcon />
        )}
      </div>
      {upload.latitude != null && upload.longitude != null && (
        <PinIcon latitude={upload.latitude} longitude={upload.longitude} />
      )}
      <div className="p-3 space-y-1 text-sm text-gray-600">
        <div className="flex items-start justify-between gap-1">
          <p className="font-medium text-gray-800 truncate" title={upload.storageKey}>
            {upload.storageKey}
          </p>
          <div className="flex-shrink-0 flex items-center gap-0.5">
            {isImage(upload.mimeType) && onRotate && (
              <button
                onClick={() => onRotate(upload.id)}
                title="Rotate 90°"
                className="text-gray-400 hover:text-gray-700 transition-colors p-0.5"
              >
                <RotateIcon />
              </button>
            )}
            {onUpdateTags && (
              <button
                onClick={() => setEditingTags(true)}
                title="Edit tags"
                className={`p-0.5 transition-colors ${editingTags ? 'text-gray-700' : 'text-gray-400 hover:text-gray-700'}`}
              >
                <TagIcon />
              </button>
            )}
          </div>
        </div>
        <p>{upload.mimeType}</p>
        <p>{formatBytes(upload.fileSize)}</p>
        <p className="text-gray-400 text-xs">{formatDate(upload.uploadedAt)}</p>
        {editingTags ? (
          <TagEditor
            currentTags={upload.tags}
            allTags={allTags}
            onSave={async (tags) => { await onUpdateTags(upload.id, tags); setEditingTags(false) }}
            onCancel={() => setEditingTags(false)}
          />
        ) : upload.tags.length > 0 ? (
          <div className="flex flex-wrap gap-1 pt-1">
            {upload.tags.map(tag => (
              <span key={tag} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded text-xs">
                {tag}
              </span>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  )
}

function Gallery({ apiKey, onSignOut }) {
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [lightbox, setLightbox] = useState(null) // { url, rotation }
  const [videoPlayer, setVideoPlayer] = useState(null)

  const allTags = useMemo(
    () => [...new Set(uploads.flatMap(u => u.tags))].sort(),
    [uploads]
  )

  const fetchUploads = useCallback(async (showSpinner = false) => {
    if (showSpinner) setRefreshing(true)
    try {
      const r = await fetch(`${API_URL}/api/content/uploads`, { headers: { 'X-Api-Key': apiKey } })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      setUploads(await r.json())
    } catch (e) {
      if (showSpinner) setError(e.message)
    } finally {
      if (showSpinner) setRefreshing(false)
      setLoading(false)
    }
  }, [apiKey])

  useEffect(() => { fetchUploads() }, [fetchUploads])

  useEffect(() => {
    if (!autoRefresh) return
    const id = setInterval(() => fetchUploads(), 10_000)
    return () => clearInterval(id)
  }, [autoRefresh, fetchUploads])

  async function handleRotate(uploadId) {
    setUploads((prev) => prev.map((u) => {
      if (u.id !== uploadId) return u
      const newRotation = ((u.rotation ?? 0) + 90) % 360
      fetch(`${API_URL}/api/content/uploads/${uploadId}/rotation`, {
        method: 'PATCH',
        headers: { 'X-Api-Key': apiKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ rotation: newRotation }),
      }).catch(() => {})
      return { ...u, rotation: newRotation }
    }))
  }

  async function handleUpdateTags(uploadId, tags) {
    const r = await fetch(`${API_URL}/api/content/uploads/${uploadId}/tags`, {
      method: 'PATCH',
      headers: { 'X-Api-Key': apiKey, 'Content-Type': 'application/json' },
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
    setUploads(prev => prev.map(u => u.id === uploadId ? updated : u))
  }

  async function handleVideoClick({ uploadId, mimeType, apiKey: key }) {
    try {
      const r = await fetch(`${API_URL}/api/content/uploads/${uploadId}/url`, {
        headers: { 'X-Api-Key': key },
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const { url } = await r.json()
      setVideoPlayer({ url, mimeType })
    } catch (e) {
      alert(`Could not load video: ${e.message}`)
    }
  }

  function closeVideoPlayer() {
    setVideoPlayer(null)
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">Heirlooms</h1>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-gray-500 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="rounded"
            />
            Auto-refresh
          </label>
          <button
            onClick={() => fetchUploads(true)}
            disabled={refreshing}
            title="Refresh"
            className="text-gray-500 hover:text-gray-800 transition-colors disabled:opacity-40"
          >
            <RefreshIcon spinning={refreshing} />
          </button>
          <button onClick={onSignOut} className="text-sm text-gray-500 hover:text-gray-800 transition-colors">
            Sign out
          </button>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 py-8">
        {loading && <p className="text-center text-gray-500 py-20">Loading uploads…</p>}
        {error && <p className="text-center text-red-500 py-20">Error: {error}</p>}
        {!loading && !error && uploads.length === 0 && (
          <p className="text-center text-gray-400 py-20">No uploads yet.</p>
        )}
        {!loading && !error && uploads.length > 0 && (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {uploads.map((upload) => (
              <UploadCard
                key={upload.id}
                upload={upload}
                apiKey={apiKey}
                onImageClick={(url, rotation) => setLightbox({ url, rotation })}
                onVideoClick={handleVideoClick}
                onRotate={handleRotate}
                onUpdateTags={handleUpdateTags}
                allTags={allTags}
              />
            ))}
          </div>
        )}
      </main>

      {lightbox && <Lightbox url={lightbox.url} rotation={lightbox.rotation} onClose={() => setLightbox(null)} />}
      {videoPlayer && (
        <VideoPlayer
          url={videoPlayer.url}
          mimeType={videoPlayer.mimeType}
          onClose={closeVideoPlayer}
        />
      )}
    </div>
  )
}

export default function App() {
  const [apiKey, setApiKey] = useState(null)
  if (!apiKey) return <LoginScreen onLogin={setApiKey} />
  return <Gallery apiKey={apiKey} onSignOut={() => setApiKey(null)} />
}
