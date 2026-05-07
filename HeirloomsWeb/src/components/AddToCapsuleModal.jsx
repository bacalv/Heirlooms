import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { BrandModal } from './BrandModal'
import { useAuth } from '../AuthContext'
import { apiFetch, formatUnlockDate, capsuleTitle } from '../api'

export function AddToCapsuleModal({ uploadId, onSuccess, onCancel }) {
  const { apiKey } = useAuth()
  const navigate = useNavigate()
  const [capsules, setCapsules] = useState([])
  const [alreadyIn, setAlreadyIn] = useState(new Set())
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([
      apiFetch('/api/capsules?state=open', apiKey).then((r) => r.ok ? r.json() : Promise.reject()),
      apiFetch(`/api/content/uploads/${uploadId}/capsules`, apiKey).then((r) => r.ok ? r.json() : { capsules: [] }),
    ]).then(([capsuleData, reverseData]) => {
      const sorted = (capsuleData.capsules ?? []).sort(
        (a, b) => new Date(a.unlock_at) - new Date(b.unlock_at),
      )
      setCapsules(sorted)
      setAlreadyIn(new Set((reverseData.capsules ?? []).map((c) => c.id)))
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [apiKey, uploadId])

  async function handleAdd() {
    if (!selected) return
    setSaving(true)
    setError(null)
    try {
      const cap = capsules.find((c) => c.id === selected)
      const current = (cap.upload_ids ?? [])
      const next = current.includes(uploadId) ? current : [...current, uploadId]

      const detailR = await apiFetch(`/api/capsules/${selected}`, apiKey)
      if (!detailR.ok) throw new Error()
      const detail = await detailR.json()
      const existingIds = (detail.uploads ?? []).map((u) => u.id)
      const merged = existingIds.includes(uploadId) ? existingIds : [...existingIds, uploadId]

      const r = await apiFetch(`/api/capsules/${selected}`, apiKey, {
        method: 'PATCH',
        body: JSON.stringify({ upload_ids: merged }),
      })
      if (!r.ok) throw new Error()
      const updatedCap = capsules.find((c) => c.id === selected)
      onSuccess(`Added to ${capsuleTitle(updatedCap.recipients)}.`)
    } catch {
      setError(true)
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <BrandModal onClose={onCancel} width="max-w-md">
        <div className="p-6 text-center text-text-muted text-sm">Loading…</div>
      </BrandModal>
    )
  }

  if (capsules.length === 0) {
    return (
      <BrandModal onClose={onCancel} width="max-w-md">
        <div className="p-6 text-center space-y-4">
          <p className="font-serif italic text-forest text-lg">No open capsules to add this to.</p>
          <button
            onClick={() => { onCancel(); navigate(`/capsules/new?include=${uploadId}`) }}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity"
          >
            Start a capsule with this
          </button>
        </div>
      </BrandModal>
    )
  }

  return (
    <BrandModal onClose={onCancel} width="max-w-md">
      <div className="p-6 space-y-4">
        <h2 className="text-base font-sans font-medium text-forest">Add this to a capsule</h2>

        <div className="space-y-2 max-h-60 overflow-y-auto">
          {capsules.map((cap) => {
            const disabled = alreadyIn.has(cap.id)
            return (
              <label
                key={cap.id}
                className={`flex items-start gap-3 p-3 rounded border cursor-pointer transition-colors ${
                  disabled
                    ? 'opacity-50 cursor-default border-forest-08'
                    : selected === cap.id
                    ? 'border-forest bg-forest-04'
                    : 'border-forest-15 hover:border-forest'
                }`}
              >
                <input
                  type="radio"
                  name="capsule"
                  value={cap.id}
                  disabled={disabled}
                  checked={selected === cap.id}
                  onChange={() => { if (!disabled) setSelected(cap.id) }}
                  className="mt-0.5"
                />
                <div>
                  <p className="text-sm font-sans text-forest">{capsuleTitle(cap.recipients)}</p>
                  <p className="text-xs text-text-muted">To open on {formatUnlockDate(cap.unlock_at)}</p>
                  {disabled && (
                    <p className="text-xs font-serif italic text-text-muted mt-0.5">Already in this capsule.</p>
                  )}
                </div>
              </label>
            )
          })}
        </div>

        {error && (
          <p className="text-sm font-serif italic text-earth">didn't take. Try again.</p>
        )}

        <div className="flex items-center justify-between pt-2">
          <button
            onClick={onCancel}
            className="px-4 py-2 rounded-button text-sm border border-forest-25 text-forest hover:border-forest transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleAdd}
            disabled={!selected || saving}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity disabled:opacity-40"
          >
            {saving ? 'Adding…' : 'Add'}
          </button>
        </div>
      </div>
    </BrandModal>
  )
}
