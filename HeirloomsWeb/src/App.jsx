import { useEffect, useState } from 'react'

const API_URL = import.meta.env.VITE_API_URL ?? ''
const API_KEY = import.meta.env.VITE_API_KEY ?? ''

const headers = { 'X-Api-Key': API_KEY }

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

function FileIcon() {
  return (
    <svg
      className="w-16 h-16 text-gray-400"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.5}
        d="M15.172 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V8.828a2 2 0 00-.586-1.414l-3.828-3.828A2 2 0 0015.172 3z"
      />
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.5}
        d="M15 3v5a1 1 0 001 1h5"
      />
    </svg>
  )
}

function Lightbox({ url, onClose }) {
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
      <img
        src={url}
        alt="Full size"
        className="max-w-[90vw] max-h-[90vh] object-contain rounded shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      />
      <button
        className="absolute top-4 right-6 text-white text-3xl font-light leading-none hover:text-gray-300"
        onClick={onClose}
      >
        &times;
      </button>
    </div>
  )
}

function UploadCard({ upload, onImageClick }) {
  const fileUrl = `${API_URL}/api/content/uploads/${upload.id}/file`

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden flex flex-col">
      <div className="bg-gray-50 h-48 flex items-center justify-center">
        {isImage(upload.mimeType) ? (
          <img
            src={fileUrl}
            alt={upload.storageKey}
            className="object-cover w-full h-full cursor-pointer hover:opacity-90 transition-opacity"
            onClick={() => onImageClick(fileUrl)}
            onError={(e) => {
              e.target.style.display = 'none'
              e.target.nextSibling.style.display = 'flex'
            }}
          />
        ) : (
          <div className="flex items-center justify-center w-full h-full">
            <FileIcon />
          </div>
        )}
        {isImage(upload.mimeType) && (
          <div className="hidden items-center justify-center w-full h-full">
            <FileIcon />
          </div>
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

export default function App() {
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lightboxUrl, setLightboxUrl] = useState(null)

  useEffect(() => {
    fetch(`${API_URL}/api/content/uploads`, { headers })
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        return r.json()
      })
      .then(setUploads)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <h1 className="text-xl font-semibold text-gray-900">Heirlooms</h1>
      </header>

      <main className="max-w-7xl mx-auto px-4 py-8">
        {loading && (
          <p className="text-center text-gray-500 py-20">Loading uploads…</p>
        )}
        {error && (
          <p className="text-center text-red-500 py-20">Error: {error}</p>
        )}
        {!loading && !error && uploads.length === 0 && (
          <p className="text-center text-gray-400 py-20">No uploads yet.</p>
        )}
        {!loading && !error && uploads.length > 0 && (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {uploads.map((upload) => (
              <UploadCard
                key={upload.id}
                upload={upload}
                onImageClick={setLightboxUrl}
              />
            ))}
          </div>
        )}
      </main>

      {lightboxUrl && (
        <Lightbox url={lightboxUrl} onClose={() => setLightboxUrl(null)} />
      )}
    </div>
  )
}
