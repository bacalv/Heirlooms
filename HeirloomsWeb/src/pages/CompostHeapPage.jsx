import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { apiFetch, formatCompactDate, formatUploadDate, daysUntilPurge, API_URL } from '../api'
import { WorkingDots } from '../brand/WorkingDots'
import { compostHeapEmptyState } from '../brand/brandStrings'

function ThumbImage({ upload, apiKey }) {
  const [blobUrl, setBlobUrl] = useState(null)

  useEffect(() => {
    const url = upload.thumbnailKey
      ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
      : `${API_URL}/api/content/uploads/${upload.id}/file`
    fetch(url, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject())
      .then((blob) => setBlobUrl(URL.createObjectURL(blob)))
      .catch(() => {})
    return () => { if (blobUrl) URL.revokeObjectURL(blobUrl) }
  }, [upload.id, apiKey])

  if (!blobUrl) return <div className="w-20 h-20 rounded bg-forest-08 flex-shrink-0" />
  return (
    <img
      src={blobUrl}
      alt={upload.storageKey}
      className="w-20 h-20 object-cover rounded border border-forest-08 flex-shrink-0"
    />
  )
}

export function CompostHeapPage() {
  const { apiKey } = useAuth()
  const navigate = useNavigate()
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [restoringId, setRestoringId] = useState(null)
  const [restoreErrors, setRestoreErrors] = useState({})
  // Pick empty-state line once on mount; hold it for the session.
  const [emptyLine] = useState(
    () => compostHeapEmptyState[Math.floor(Math.random() * compostHeapEmptyState.length)]
  )

  useEffect(() => {
    document.title = 'Compost heap · Heirlooms'
    apiFetch('/api/content/uploads/composted', apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data) => { setUploads(data.uploads ?? []); setLoading(false) })
      .catch((e) => { setError(e.message); setLoading(false) })
  }, [apiKey])

  async function handleRestore(uploadId) {
    setRestoringId(uploadId)
    setRestoreErrors((prev) => { const n = { ...prev }; delete n[uploadId]; return n })
    try {
      const r = await apiFetch(`/api/content/uploads/${uploadId}/restore`, apiKey, { method: 'POST' })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      setUploads((prev) => prev.filter((u) => u.id !== uploadId))
    } catch (e) {
      setRestoreErrors((prev) => ({ ...prev, [uploadId]: "Couldn't restore. Try again." }))
    } finally {
      setRestoringId(null)
    }
  }

  if (loading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <WorkingDots size="lg" />
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 space-y-6">
      <Link to="/" className="text-sm text-text-muted hover:text-forest transition-colors">← Garden</Link>

      <h1 className="text-2xl font-sans font-medium text-forest">Compost heap</h1>

      {error && (
        <p className="font-serif italic text-earth text-sm">Something went wrong — {error}</p>
      )}

      {!error && uploads.length === 0 && (
        <div className="py-16 flex justify-center">
          <p className="font-serif italic text-forest text-xl text-center">{emptyLine}</p>
        </div>
      )}

      {!error && uploads.length > 0 && (
        <div className="space-y-0">
          {uploads.map((upload, i) => {
            const days = daysUntilPurge(upload.compostedAt)
            return (
              <div key={upload.id}>
                {i > 0 && <div className="border-t border-forest-08" />}
                <div className="py-4 flex items-center gap-4">
                  <Link to={`/photos/${upload.id}`} className="flex-shrink-0">
                    <ThumbImage upload={upload} apiKey={apiKey} />
                  </Link>
                  <div className="flex-1 min-w-0">
                    <Link to={`/photos/${upload.id}`} className="block">
                      <p className="text-sm text-forest font-sans">
                        Uploaded {formatCompactDate(upload.uploadedAt)}
                      </p>
                      <p className="text-xs text-text-muted truncate">{upload.storageKey}</p>
                    </Link>
                    <p className="text-xs text-text-muted mt-0.5">
                      Composted {formatCompactDate(upload.compostedAt)}. {days} {days === 1 ? 'day' : 'days'} left.
                    </p>
                    {restoreErrors[upload.id] && (
                      <p className="text-xs text-earth mt-0.5">{restoreErrors[upload.id]}</p>
                    )}
                  </div>
                  <button
                    onClick={() => handleRestore(upload.id)}
                    disabled={restoringId === upload.id}
                    className="flex-shrink-0 px-3 py-1.5 rounded-button border border-forest text-forest text-sm hover:bg-forest-08 transition-colors disabled:opacity-40"
                  >
                    {restoringId === upload.id ? <WorkingDots size="sm" /> : 'Restore'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
