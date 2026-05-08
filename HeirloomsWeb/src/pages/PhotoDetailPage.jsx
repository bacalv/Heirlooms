import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import { apiFetch, daysUntilPurge, formatCompactDate, formatUploadDate, capsuleTitle } from '../api'
import { WaxSealOlive } from '../brand/WaxSealOlive'
import { WorkingDots } from '../brand/WorkingDots'
import { AddToCapsuleModal } from '../components/AddToCapsuleModal'
import { Toast } from '../components/Toast'
import { API_URL } from '../api'

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function KebabIcon() {
  return (
    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
      <circle cx="12" cy="5" r="1.5" />
      <circle cx="12" cy="12" r="1.5" />
      <circle cx="12" cy="19" r="1.5" />
    </svg>
  )
}

// ---- Kebab menu for Explore flavour ----------------------------------------

function KebabMenu({ onAddToCapsule, onCompost, compostDisabled }) {
  const [open, setOpen] = useState(false)

  useEffect(() => {
    function handleClick() { setOpen(false) }
    if (open) document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <div className="relative" onMouseDown={(e) => e.stopPropagation()}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="p-1 text-text-muted hover:text-forest transition-colors rounded"
        title="Actions"
        aria-label="Actions"
      >
        <KebabIcon />
      </button>
      {open && (
        <div className="absolute right-0 top-7 z-20 w-44 bg-white border border-forest-15 rounded shadow-md text-sm py-1">
          <button className="w-full text-left px-3 py-1.5 hover:bg-forest-04 text-forest"
            onClick={() => { setOpen(false); onAddToCapsule() }}>
            Add to a capsule
          </button>
          <div className="border-t border-forest-08 my-1" />
          <button
            className="w-full text-left px-3 py-1.5 hover:bg-earth-10 text-earth disabled:opacity-40"
            disabled={compostDisabled}
            onClick={() => { setOpen(false); onCompost() }}
          >
            Compost
          </button>
        </div>
      )}
    </div>
  )
}

// ---- Garden flavour (default) — action-forward layout ----------------------

function GardenFlavour({ upload, blobUrl, capsules, isComposted, composting, compostError,
  restoring, restoreError, compostDisabled, onCompost, onRestore, onOpenAddModal }) {

  const isImage = upload.mimeType?.startsWith('image/')
  const isVideo = upload.mimeType?.startsWith('video/')
  const activeCapsules = capsules.filter((c) => c.state === 'open' || c.state === 'sealed')
  const imageStyle = {
    ...(upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : {}),
    ...(isComposted ? { filter: 'saturate(0.6) opacity(0.85)' } : {}),
  }

  return (
    <div className="space-y-8">
      {blobUrl && isImage && (
        <div className="rounded-card overflow-hidden border border-forest-08 bg-forest-04 flex items-center justify-center" style={{ minHeight: 200, maxHeight: 500 }}>
          <img src={blobUrl} alt={upload.storageKey} className="max-w-full max-h-[500px] object-contain"
            style={Object.keys(imageStyle).length ? imageStyle : undefined} />
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
        {!isComposted && upload.tags?.length > 0 && (
          <div className="flex flex-wrap gap-1 pt-1">
            {upload.tags.map((tag) => (
              <span key={tag} className="px-[9px] py-[3px] rounded-chip bg-forest-08 text-forest text-[11px]">{tag}</span>
            ))}
          </div>
        )}
      </div>

      {isComposted && (
        <div className="border-t border-forest-15 pt-6 space-y-3">
          <p className="text-sm text-text-muted">
            Composted on {formatCompactDate(upload.compostedAt)}. Will be permanently deleted in {daysUntilPurge(upload.compostedAt)} {daysUntilPurge(upload.compostedAt) === 1 ? 'day' : 'days'}.
          </p>
          <button onClick={onRestore} disabled={restoring}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity disabled:opacity-40">
            {restoring ? <WorkingDots size="sm" /> : 'Restore'}
          </button>
          {restoreError && <p className="text-xs text-earth">{restoreError}</p>}
        </div>
      )}

      {!isComposted && (
        <div className="border-t border-forest-15 pt-6 space-y-3">
          {activeCapsules.length > 0 && (
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
          )}
          <button onClick={onOpenAddModal}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity">
            Add this to a capsule
          </button>

          {/* Negative-action separation: Compost is visually isolated below a divider */}
          <div className="pt-2 border-t border-forest-08">
            <button onClick={onCompost} disabled={compostDisabled || composting}
              className="px-4 py-2 rounded-button text-sm border border-earth text-earth hover:bg-earth-10 transition-colors disabled:opacity-40">
              {composting ? <WorkingDots size="sm" /> : 'Compost'}
            </button>
            {compostDisabled && !composting && (
              <p className="text-xs font-sans italic text-text-muted mt-1">
                Compost requires no tags and no active capsule memberships.
              </p>
            )}
            {compostError && <p className="text-xs text-earth mt-1">{compostError}</p>}
          </div>
        </div>
      )}
    </div>
  )
}

// ---- Explore flavour — content-forward layout ------------------------------

function ExploreFlavour({ upload, blobUrl, capsules, isComposted, composting, compostError,
  restoring, restoreError, compostDisabled, onCompost, onRestore, onOpenAddModal }) {

  const isImage = upload.mimeType?.startsWith('image/')
  const isVideo = upload.mimeType?.startsWith('video/')
  const activeCapsules = capsules.filter((c) => c.state === 'open' || c.state === 'sealed')
  const imageStyle = {
    ...(upload.rotation ? { transform: `rotate(${upload.rotation}deg)` } : {}),
    ...(isComposted ? { filter: 'saturate(0.6) opacity(0.85)' } : {}),
  }

  return (
    <div className="space-y-6">
      {/* Larger hero */}
      {blobUrl && isImage && (
        <div className="rounded-card overflow-hidden border border-forest-08 bg-forest-04 flex items-center justify-center" style={{ minHeight: 240, maxHeight: 640 }}>
          <img src={blobUrl} alt={upload.storageKey} className="max-w-full max-h-[640px] object-contain"
            style={Object.keys(imageStyle).length ? imageStyle : undefined} />
        </div>
      )}
      {blobUrl && isVideo && (
        <div className="rounded-card overflow-hidden border border-forest-08 bg-black">
          <video src={blobUrl} controls className="max-w-full max-h-[600px] w-full" />
        </div>
      )}

      {/* Metadata prominent */}
      <div className="space-y-1.5">
        <div className="flex items-start justify-between gap-2">
          <h1 className="text-base font-medium text-forest">{upload.storageKey}</h1>
          {!isComposted && (
            <KebabMenu
              onAddToCapsule={onOpenAddModal}
              onCompost={onCompost}
              compostDisabled={compostDisabled || composting}
            />
          )}
        </div>
        {upload.capturedAt && (
          <p className="text-sm text-text-body">
            Taken {formatUploadDate(upload.capturedAt)}
          </p>
        )}
        <p className="text-sm text-text-muted">Uploaded {formatUploadDate(upload.uploadedAt)}</p>
        {upload.latitude != null && upload.longitude != null && (
          <p className="text-sm text-text-muted">
            {upload.latitude.toFixed(5)}, {upload.longitude.toFixed(5)}
          </p>
        )}
        {activeCapsules.length > 0 && (
          <p className="text-sm text-text-muted">
            In {activeCapsules.length} {activeCapsules.length === 1 ? 'capsule' : 'capsules'}
          </p>
        )}
        <p className="text-sm text-text-muted">{upload.mimeType} · {formatBytes(upload.fileSize)}</p>
      </div>

      {/* Tags (read-only) with link to Garden flavour to edit */}
      {upload.tags?.length > 0 ? (
        <div className="flex flex-wrap gap-1 items-center">
          {upload.tags.map((tag) => (
            <span key={tag} className="px-[9px] py-[3px] rounded-chip bg-forest-08 text-forest text-[11px]">{tag}</span>
          ))}
          <Link to={`/photos/${upload.id}?from=garden`} className="text-[11px] text-text-muted hover:text-forest transition-colors ml-1">
            Edit tags
          </Link>
        </div>
      ) : (
        <Link to={`/photos/${upload.id}?from=garden`} className="text-xs text-text-muted hover:text-forest transition-colors">
          Add tags
        </Link>
      )}

      {isComposted && (
        <div className="border-t border-forest-15 pt-4 space-y-3">
          <p className="text-sm text-text-muted">
            Composted on {formatCompactDate(upload.compostedAt)}. Will be permanently deleted in {daysUntilPurge(upload.compostedAt)} {daysUntilPurge(upload.compostedAt) === 1 ? 'day' : 'days'}.
          </p>
          <button onClick={onRestore} disabled={restoring}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity disabled:opacity-40">
            {restoring ? <WorkingDots size="sm" /> : 'Restore'}
          </button>
          {restoreError && <p className="text-xs text-earth">{restoreError}</p>}
        </div>
      )}

      {compostError && <p className="text-xs text-earth">{compostError}</p>}
    </div>
  )
}

// ---- Main page -------------------------------------------------------------

export function PhotoDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { apiKey } = useAuth()

  const from = searchParams.get('from') ?? 'garden' // default to garden flavour

  const [upload, setUpload] = useState(location.state?.upload ?? null)
  const [blobUrl, setBlobUrl] = useState(null)
  const [capsules, setCapsules] = useState([])
  const [loadingUpload, setLoadingUpload] = useState(!location.state?.upload)
  const [showAddModal, setShowAddModal] = useState(false)
  const [toast, setToast] = useState(null)
  const [composting, setComposting] = useState(false)
  const [compostError, setCompostError] = useState(null)
  const [restoring, setRestoring] = useState(false)
  const [restoreError, setRestoreError] = useState(null)

  useEffect(() => {
    document.title = upload ? `${upload.storageKey} · Heirlooms` : 'Photo · Heirlooms'
  }, [upload])

  useEffect(() => {
    if (upload) return
    apiFetch(`/api/content/uploads/${id}`, apiKey)
      .then((r) => {
        if (r.status === 404) return null
        if (!r.ok) return Promise.reject(new Error(`HTTP ${r.status}`))
        return r.json()
      })
      .then((data) => { setUpload(data); setLoadingUpload(false) })
      .catch(() => setLoadingUpload(false))
  }, [apiKey, id, upload])

  // Record the view to remove from "Just arrived"
  useEffect(() => {
    apiFetch(`/api/content/uploads/${id}/view`, apiKey, { method: 'POST' }).catch(() => {})
  }, [apiKey, id])

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

  async function handleCompost() {
    setComposting(true)
    setCompostError(null)
    try {
      const r = await apiFetch(`/api/content/uploads/${upload.id}/compost`, apiKey, { method: 'POST' })
      if (!r.ok) throw new Error('failed')
      navigate('/', { state: { composted: true } })
    } catch {
      setCompostError("Couldn't compost. Try again.")
      setComposting(false)
    }
  }

  async function handleRestore() {
    setRestoring(true)
    setRestoreError(null)
    try {
      const r = await apiFetch(`/api/content/uploads/${upload.id}/restore`, apiKey, { method: 'POST' })
      if (!r.ok) throw new Error('failed')
      const restored = await r.json()
      setUpload(restored)
    } catch {
      setRestoreError("Couldn't restore. Try again.")
    } finally {
      setRestoring(false)
    }
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

  const isComposted = !!upload.compostedAt
  const activeCapsules = capsules.filter((c) => c.state === 'open' || c.state === 'sealed')
  const compostDisabled = (upload.tags?.length > 0) || activeCapsules.length > 0

  const backTo = isComposted ? '/compost' : (from === 'explore' ? '/explore' : '/')
  const backLabel = isComposted ? '← Compost heap' : (from === 'explore' ? '← Explore' : '← Garden')

  const sharedProps = {
    upload, blobUrl, capsules, isComposted,
    composting, compostError, compostDisabled, onCompost: handleCompost,
    restoring, restoreError, onRestore: handleRestore,
    onOpenAddModal: () => setShowAddModal(true),
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 space-y-6">
      <Link to={backTo} className="text-sm text-text-muted hover:text-forest transition-colors">
        {backLabel}
      </Link>

      {from === 'explore' ? (
        <ExploreFlavour {...sharedProps} />
      ) : (
        <GardenFlavour {...sharedProps} />
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
