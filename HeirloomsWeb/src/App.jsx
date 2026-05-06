import { useEffect, useRef, useState } from 'react'

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

function Lightbox({ url, onClose }) {
  return (
    <Modal onClose={onClose}>
      <img
        src={url}
        alt="Full size"
        className="max-w-[90vw] max-h-[90vh] object-contain rounded shadow-2xl"
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

function UploadCard({ upload, apiKey, onImageClick, onVideoClick }) {
  const fileUrl = `${API_URL}/api/content/uploads/${upload.id}/file`
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : fileUrl
  const [blobUrl, setBlobUrl] = useState(null)

  // Pre-fetch image thumbnails on mount so they display in the grid.
  // Videos are fetched on demand when the user clicks, to avoid loading
  // large files until they're actually wanted.
  useEffect(() => {
    if (!isImage(upload.mimeType)) return
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
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden flex flex-col">
      <div className="bg-gray-50 h-48 flex items-center justify-center">
        {isImage(upload.mimeType) ? (
          blobUrl ? (
            <img
              src={blobUrl}
              alt={upload.storageKey}
              className="object-cover w-full h-full cursor-pointer hover:opacity-90 transition-opacity"
              onClick={() => onImageClick(blobUrl)}
            />
          ) : (
            <Spinner />
          )
        ) : isVideo(upload.mimeType) ? (
          <button
            className="flex flex-col items-center gap-2 hover:opacity-70 transition-opacity cursor-pointer"
            onClick={handleVideoClick}
          >
            <VideoIcon />
            <span className="text-xs text-gray-400">Click to play</span>
          </button>
        ) : (
          <FileIcon />
        )}
      </div>
      <div className="p-3 space-y-1 text-sm text-gray-600">
        <p className="font-medium text-gray-800 truncate" title={upload.storageKey}>
          {upload.storageKey}
        </p>
        <p>{upload.mimeType}</p>
        <p>{formatBytes(upload.fileSize)}</p>
        <p className="text-gray-400 text-xs">{formatDate(upload.uploadedAt)}</p>
      </div>
    </div>
  )
}

function Gallery({ apiKey, onSignOut }) {
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lightboxUrl, setLightboxUrl] = useState(null)
  const [videoPlayer, setVideoPlayer] = useState(null) // { url, mimeType }

  useEffect(() => {
    fetch(`${API_URL}/api/content/uploads`, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json() })
      .then(setUploads)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [apiKey])

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
        <button onClick={onSignOut} className="text-sm text-gray-500 hover:text-gray-800 transition-colors">
          Sign out
        </button>
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
                onImageClick={setLightboxUrl}
                onVideoClick={handleVideoClick}
              />
            ))}
          </div>
        )}
      </main>

      {lightboxUrl && <Lightbox url={lightboxUrl} onClose={() => setLightboxUrl(null)} />}
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
