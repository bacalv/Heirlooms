import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { apiFetch, formatUploadDate, capsuleTitle } from '../api'
import { WaxSealOlive } from '../brand/WaxSealOlive'
import { AddToCapsuleModal } from '../components/AddToCapsuleModal'
import { Toast } from '../components/Toast'
import { API_URL } from '../api'

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function PhotoDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const { apiKey } = useAuth()
  const [upload, setUpload] = useState(location.state?.upload ?? null)
  const [blobUrl, setBlobUrl] = useState(null)
  const [capsules, setCapsules] = useState([])
  const [loadingUpload, setLoadingUpload] = useState(!location.state?.upload)
  const [showAddModal, setShowAddModal] = useState(false)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    document.title = upload ? `${upload.storageKey} · Heirlooms` : 'Photo · Heirlooms'
  }, [upload])

  useEffect(() => {
    if (upload) return
    apiFetch('/api/content/uploads', apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject())
      .then((data) => {
        const found = data.find((u) => u.id === id)
        setUpload(found ?? null)
        setLoadingUpload(false)
      })
      .catch(() => setLoadingUpload(false))
  }, [apiKey, id, upload])

  useEffect(() => {
    if (!upload) return
    const displayUrl = upload.thumbnailKey
      ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
      : `${API_URL}/api/content/uploads/${upload.id}/file`
    fetch(displayUrl, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject())
      .then((blob) => setBlobUrl(URL.createObjectURL(blob)))
      .catch(() => {})
  }, [apiKey, upload])

  useEffect(() => {
    apiFetch(`/api/content/uploads/${id}/capsules`, apiKey)
      .then((r) => r.ok ? r.json() : { capsules: [] })
      .then((data) => setCapsules(data.capsules ?? []))
      .catch(() => {})
  }, [apiKey, id])

  function handleAddSuccess(message) {
    setShowAddModal(false)
    setToast(message)
    apiFetch(`/api/content/uploads/${id}/capsules`, apiKey)
      .then((r) => r.ok ? r.json() : { capsules: [] })
      .then((data) => setCapsules(data.capsules ?? []))
      .catch(() => {})
  }

  if (loadingUpload) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="text-text-muted text-sm">Loading…</div>
      </div>
    )
  }

  if (!upload) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8 text-center space-y-4">
        <p className="font-serif italic text-earth">Not found</p>
        <p className="text-sm text-text-body">This photo doesn't exist or has been removed.</p>
        <Link to="/" className="text-sm text-forest underline">Back to Garden</Link>
      </div>
    )
  }

  const isImage = upload.mimeType?.startsWith('image/')
  const isVideo = upload.mimeType?.startsWith('video/')

  const activeCapsules = capsules.filter((c) => c.state === 'open' || c.state === 'sealed')

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 space-y-8">
      <Link to="/" className="text-sm text-text-muted hover:text-forest transition-colors">← Garden</Link>

      {blobUrl && isImage && (
        <div className="rounded-card overflow-hidden border border-forest-08 bg-forest-04 flex items-center justify-center" style={{ minHeight: 200, maxHeight: 500 }}>
          <img src={blobUrl} alt={upload.storageKey}
            className="max-w-full max-h-[500px] object-contain"
            style={upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : undefined} />
        </div>
      )}

      {blobUrl && isVideo && (
        <div className="rounded-card overflow-hidden border border-forest-08 bg-black">
          <video src={blobUrl} controls className="max-w-full max-h-[500px] w-full" />
        </div>
      )}

      <div className="space-y-2">
        <h1 className="text-lg font-medium text-forest">{upload.storageKey}</h1>
        <p className="text-sm text-text-muted">{upload.mimeType} · {formatBytes(upload.fileSize)}</p>
        <p className="text-sm text-text-muted">Added {formatUploadDate(upload.uploadedAt)}</p>
        {upload.tags?.length > 0 && (
          <div className="flex flex-wrap gap-1 pt-1">
            {upload.tags.map((tag) => (
              <span key={tag} className="px-[9px] py-[3px] rounded-chip bg-forest-08 text-forest text-[11px]">{tag}</span>
            ))}
          </div>
        )}
      </div>

      {activeCapsules.length > 0 && (
        <div className="border-t border-forest-15 pt-6 space-y-3">
          <p className="text-sm text-forest">
            In capsules:{' '}
            {activeCapsules.map((cap, i) => (
              <span key={cap.id}>
                {i > 0 && ', '}
                <Link to={`/capsules/${cap.id}`} className="text-forest underline hover:no-underline">
                  {capsuleTitle(cap.recipients)}
                </Link>
                {cap.state === 'sealed' && (
                  <span className="inline-flex items-end ml-1 align-middle">
                    <WaxSealOlive size={14} />
                  </span>
                )}
              </span>
            ))}
          </p>
          <button
            onClick={() => setShowAddModal(true)}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity"
          >
            Add this to a capsule
          </button>
        </div>
      )}

      {activeCapsules.length === 0 && (
        <div className="border-t border-forest-15 pt-6">
          <button
            onClick={() => setShowAddModal(true)}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity"
          >
            Add this to a capsule
          </button>
        </div>
      )}

      {showAddModal && (
        <AddToCapsuleModal
          uploadId={upload.id}
          onSuccess={handleAddSuccess}
          onCancel={() => setShowAddModal(false)}
        />
      )}

      {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
    </div>
  )
}
