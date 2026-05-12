import { useEffect, useState } from 'react'
import { apiFetch } from '../api'
import { UploadThumb } from './UploadThumb'
import { WorkingDots } from '../brand/WorkingDots'

function StagingThumb({ upload, onApprove, onReject, approving, rejecting }) {
  const isVideo = upload.mimeType?.startsWith('video/')
  return (
    <div className="relative group">
      <div className="aspect-square overflow-hidden rounded border border-forest-08 bg-forest-04">
        <UploadThumb upload={upload} className="w-full h-full object-contain" rotation={upload.rotation} alt="" />
        {isVideo && (
          <div className="absolute bottom-0 right-0 bg-forest-75 rounded-tl p-0.5 pointer-events-none">
            <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z" /></svg>
          </div>
        )}
      </div>
      <div className="flex gap-1 mt-1">
        <button onClick={() => onApprove(upload.id)} disabled={approving || rejecting}
          className="flex-1 px-2 py-1 text-xs bg-forest text-parchment rounded-button hover:opacity-90 transition-opacity disabled:opacity-40">
          {approving ? '…' : '✓'}
        </button>
        <button onClick={() => onReject(upload.id)} disabled={approving || rejecting}
          className="flex-1 px-2 py-1 text-xs border border-forest-25 text-text-muted rounded-button hover:border-earth hover:text-earth transition-colors disabled:opacity-40">
          {rejecting ? '…' : '✕'}
        </button>
      </div>
    </div>
  )
}

export function StagingPanel({ plotId, flowId, apiKey, onItemActioned }) {
  const [pending, setPending] = useState([])
  const [rejected, setRejected] = useState([])
  const [loading, setLoading] = useState(true)
  const [showRejected, setShowRejected] = useState(false)
  const [working, setWorking] = useState({}) // uploadId → 'approving'|'rejecting'

  const stagingUrl = flowId
    ? `/api/flows/${flowId}/staging`
    : `/api/plots/${plotId}/staging`

  function reload() {
    setLoading(true)
    Promise.all([
      apiFetch(stagingUrl, apiKey).then((r) => r.ok ? r.json() : []),
      apiFetch(`/api/plots/${plotId}/staging/rejected`, apiKey).then((r) => r.ok ? r.json() : []),
    ])
      .then(([p, r]) => { setPending(Array.isArray(p) ? p : []); setRejected(Array.isArray(r) ? r : []) })
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => { reload() }, [plotId, flowId, apiKey])

  async function handleApprove(uploadId) {
    setWorking((w) => ({ ...w, [uploadId]: 'approving' }))
    try {
      await apiFetch(`/api/plots/${plotId}/staging/${uploadId}/approve`, apiKey, {
        method: 'POST', body: '{}',
      })
      onItemActioned?.()
      reload()
    } finally {
      setWorking((w) => { const n = { ...w }; delete n[uploadId]; return n })
    }
  }

  async function handleReject(uploadId) {
    setWorking((w) => ({ ...w, [uploadId]: 'rejecting' }))
    try {
      await apiFetch(`/api/plots/${plotId}/staging/${uploadId}/reject`, apiKey, {
        method: 'POST', body: '{}',
      })
      reload()
    } finally {
      setWorking((w) => { const n = { ...w }; delete n[uploadId]; return n })
    }
  }

  async function handleUnReject(uploadId) {
    setWorking((w) => ({ ...w, [uploadId]: 'approving' }))
    try {
      await apiFetch(`/api/plots/${plotId}/staging/${uploadId}/decision`, apiKey, { method: 'DELETE' })
      reload()
    } finally {
      setWorking((w) => { const n = { ...w }; delete n[uploadId]; return n })
    }
  }

  if (loading) return (
    <div className="flex justify-center py-8">
      <WorkingDots size="sm" label="Loading staging…" />
    </div>
  )

  return (
    <div className="space-y-4">
      {pending.length === 0 ? (
        <p className="text-text-muted text-sm font-sans text-center py-6">Nothing waiting for review.</p>
      ) : (
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-2">
          {pending.map((u) => (
            <StagingThumb key={u.id} upload={u}
              onApprove={handleApprove} onReject={handleReject}
              approving={working[u.id] === 'approving'}
              rejecting={working[u.id] === 'rejecting'}
            />
          ))}
        </div>
      )}

      {rejected.length > 0 && (
        <div>
          <button onClick={() => setShowRejected((s) => !s)}
            className="text-xs text-text-muted hover:text-forest transition-colors font-sans">
            {showRejected ? '▾' : '▸'} {rejected.length} rejected item{rejected.length !== 1 ? 's' : ''}
          </button>
          {showRejected && (
            <div className="mt-2 grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-2">
              {rejected.map((u) => (
                <div key={u.id} className="relative group">
                  <div className="aspect-square overflow-hidden rounded border border-forest-08 opacity-50">
                    <UploadThumb upload={u} className="w-full h-full object-contain" rotation={u.rotation} alt="" />
                  </div>
                  <button onClick={() => handleUnReject(u.id)} disabled={!!working[u.id]}
                    className="mt-1 w-full px-2 py-1 text-xs border border-forest-15 text-text-muted rounded-button hover:border-forest hover:text-forest transition-colors disabled:opacity-40">
                    {working[u.id] ? '…' : 'Restore'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
