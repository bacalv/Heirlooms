import { useState, useEffect } from 'react'
import { BrandModal } from './BrandModal'
import { useAuth } from '../AuthContext'
import { apiFetch, listPlots, addPlotItem } from '../api'
import {
  unwrapDekWithMasterKey, unwrapWithSharingKey, unwrapPlotKey,
  wrapDekWithPlotKey, fromB64, toB64, ALG_P256_ECDH_HKDF_V1, ALG_PLOT_AES256GCM_V1,
} from '../crypto/vaultCrypto'
import { getMasterKey, getPlotKey, setPlotKey, getSharingPrivkey } from '../crypto/vaultSession'

// Load (and cache) the plot key for a given shared plot.
async function loadSharedPlotKey(plotId, apiKey) {
  const cached = getPlotKey(plotId)
  if (cached) return cached
  const resp = await apiFetch(`/api/plots/${plotId}/plot-key`, apiKey)
  if (!resp.ok) throw new Error('Could not fetch plot key')
  const { wrappedPlotKey } = await resp.json()
  const sharingPrivkey = getSharingPrivkey()
  if (!sharingPrivkey) throw new Error('Sharing key not loaded — try logging in again')
  const plotKeyBytes = await unwrapPlotKey(fromB64(wrappedPlotKey), sharingPrivkey)
  setPlotKey(plotId, plotKeyBytes)
  return plotKeyBytes
}

// Unwrap a DEK using whatever format it was wrapped with.
async function unwrapUploadDek(wrappedB64, fmt) {
  if (fmt === ALG_P256_ECDH_HKDF_V1) {
    const privkey = getSharingPrivkey()
    if (!privkey) throw new Error('Sharing key not available')
    return unwrapWithSharingKey(fromB64(wrappedB64), privkey)
  }
  if (fmt === ALG_PLOT_AES256GCM_V1) {
    // The DEK is wrapped under a plot key — the caller should handle this via
    // the plot-key unwrap path, but we handle it here as a fallback.
    throw new Error('Cannot re-wrap a plot-keyed DEK without knowing the source plot key')
  }
  // Default: master-key wrapping.
  const masterKey = getMasterKey()
  if (!masterKey) throw new Error('Master key not available')
  return unwrapDekWithMasterKey(fromB64(wrappedB64), masterKey)
}

async function buildAddToPlotBody(upload, plotId, apiKey) {
  if (!upload.wrappedDek) {
    throw new Error('This photo is not encrypted and cannot be added to a shared plot')
  }
  const plotKeyBytes = await loadSharedPlotKey(plotId, apiKey)

  const rawDek = await unwrapUploadDek(upload.wrappedDek, upload.dekFormat)
  const { wrappedDek: wrappedItemDek, format: itemDekFormat } = await wrapDekWithPlotKey(rawDek, plotKeyBytes)
  const body = {
    uploadId: upload.id,
    wrappedItemDek: toB64(wrappedItemDek),
    itemDekFormat,
  }

  if (upload.wrappedThumbnailDek) {
    const rawThumb = await unwrapUploadDek(upload.wrappedThumbnailDek, upload.thumbnailDekFormat)
    const { wrappedDek: wrappedThumbDek, format: thumbFormat } = await wrapDekWithPlotKey(rawThumb, plotKeyBytes)
    body.wrappedThumbnailDek = toB64(wrappedThumbDek)
    body.thumbnailDekFormat = thumbFormat
  }

  return body
}

export function AddToPlotModal({ upload, onSuccess, onCancel }) {
  const { apiKey } = useAuth()
  const [sharedPlots, setSharedPlots] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    listPlots(apiKey)
      .then((data) => {
        const plots = Array.isArray(data) ? data : (data.plots ?? [])
        setSharedPlots(plots.filter((p) => p.visibility === 'shared'))
        setLoading(false)
      })
      .catch(() => setLoading(false))
  }, [apiKey])

  async function handleAdd() {
    if (!selected) return
    setSaving(true)
    setError(null)
    try {
      const body = await buildAddToPlotBody(upload, selected, apiKey)
      await addPlotItem(apiKey, selected, body)
      const plot = sharedPlots.find((p) => p.id === selected)
      const plotName = plot?.local_name ?? plot?.localName ?? plot?.name ?? 'shared plot'
      onSuccess(`Added to ${plotName}.`)
    } catch (e) {
      if (e.message === 'already_present') {
        setError('This photo is already in that plot.')
      } else {
        setError(e.message ?? "Couldn't add to plot. Try again.")
      }
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

  if (sharedPlots.length === 0) {
    return (
      <BrandModal onClose={onCancel} width="max-w-md">
        <div className="p-6 text-center space-y-4">
          <p className="font-serif italic text-forest text-lg">You're not a member of any shared plots yet.</p>
          <button
            onClick={onCancel}
            className="px-4 py-2 rounded-button text-sm border border-forest-25 text-forest hover:border-forest transition-colors"
          >
            Close
          </button>
        </div>
      </BrandModal>
    )
  }

  return (
    <BrandModal onClose={onCancel} width="max-w-md">
      <div className="p-6 space-y-4">
        <h2 className="text-base font-sans font-medium text-forest">Add to a shared plot</h2>

        <div className="space-y-2 max-h-60 overflow-y-auto">
          {sharedPlots.map((plot) => {
            const displayName = plot.local_name ?? plot.localName ?? plot.name
            return (
              <label
                key={plot.id}
                className={`flex items-start gap-3 p-3 rounded border cursor-pointer transition-colors ${
                  selected === plot.id
                    ? 'border-forest bg-forest-04'
                    : 'border-forest-15 hover:border-forest'
                }`}
              >
                <input
                  type="radio"
                  name="plot"
                  value={plot.id}
                  checked={selected === plot.id}
                  onChange={() => setSelected(plot.id)}
                  className="mt-0.5"
                />
                <div>
                  <p className="text-sm font-sans text-forest">{displayName}</p>
                  {plot.name !== displayName && (
                    <p className="text-xs text-text-muted">{plot.name}</p>
                  )}
                </div>
              </label>
            )
          })}
        </div>

        {error && (
          <p className="text-sm text-earth">{error}</p>
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
            {saving ? 'Adding…' : 'Add to plot'}
          </button>
        </div>
      </div>
    </BrandModal>
  )
}
