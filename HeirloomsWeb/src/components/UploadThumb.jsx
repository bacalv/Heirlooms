import { useEffect, useState } from 'react'
import { useAuth } from '../AuthContext'
import { API_URL } from '../api'
import { getThumb } from '../thumbCache'
import { getMasterKey, getSharingPrivkey, thumbnailCache, cacheThumbnail, getPlotKey, setPlotKey } from '../crypto/vaultSession'
import { unwrapDekWithMasterKey, unwrapWithSharingKey, unwrapPlotKey, unwrapDekWithPlotKey, decryptSymmetric, fromB64, ALG_P256_ECDH_HKDF_V1, ALG_PLOT_AES256GCM_V1 } from '../crypto/vaultCrypto'
import { apiFetch } from '../api'
import { OliveBranchIcon } from '../brand/OliveBranchIcon'

// Drop-in replacement for <img> wherever an `upload` object is in scope.
// Handles encrypted and public storage classes transparently.
export function UploadThumb({ upload, className, style, alt = '', rotation, plotId }) {
  if (!upload) return <div className={`bg-forest-08 ${className ?? ''}`} style={style} />
  if (upload.storageClass === 'encrypted') {
    return <EncryptedThumb upload={upload} className={className} style={style} alt={alt} rotation={rotation} plotId={plotId} />
  }
  return <PlaintextThumb upload={upload} className={className} style={style} alt={alt} rotation={rotation} />
}

function PlaintextThumb({ upload, className, style, alt, rotation }) {
  const { apiKey } = useAuth()
  const isImage = upload.mimeType?.startsWith('image/')
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : (isImage ? `${API_URL}/api/content/uploads/${upload.id}/file` : null)

  const [blobUrl, setBlobUrl] = useState(null)

  useEffect(() => {
    if (!displayUrl) return
    let cancelled = false
    let ownUrl = null
    getThumb(upload.id, displayUrl, apiKey)
      .then((url) => {
        if (!cancelled) { ownUrl = url; setBlobUrl(url) }
        else URL.revokeObjectURL(url)
      })
      .catch(() => {})
    return () => { cancelled = true; if (ownUrl) URL.revokeObjectURL(ownUrl) }
  }, [upload.id, displayUrl, apiKey])

  if (!blobUrl) return <div className={`bg-forest-08 ${className ?? ''}`} style={style} />
  const rotateStyle = rotation ? { transform: `rotate(${rotation}deg)` } : {}
  return <img src={blobUrl} alt={alt} className={className} style={{ ...rotateStyle, ...style }} />
}

async function loadPlotKey(plotId, apiKey) {
  const cached = getPlotKey(plotId)
  if (cached) return cached
  const resp = await apiFetch(`/api/plots/${plotId}/plot-key`, apiKey)
  if (!resp.ok) throw new Error('Could not fetch plot key')
  const { wrappedPlotKey } = await resp.json()
  const plotKeyBytes = await unwrapPlotKey(fromB64(wrappedPlotKey), getSharingPrivkey())
  setPlotKey(plotId, plotKeyBytes)
  return plotKeyBytes
}

function EncryptedThumb({ upload, className, style, alt, rotation, plotId }) {
  const { apiKey } = useAuth()
  const cached = thumbnailCache.get(upload.id) ?? null
  const [src, setSrc] = useState(cached)
  const [loading, setLoading] = useState(!cached)

  useEffect(() => {
    if (thumbnailCache.has(upload.id)) return
    let cancelled = false
    ;(async () => {
      try {
        const masterKey = getMasterKey()
        const raw = upload.wrappedThumbnailDek
        if (!raw) return
        const wrappedDek = typeof raw === 'string' ? fromB64(raw) : raw
        let thumbDek
        if (upload.thumbnailDekFormat === ALG_P256_ECDH_HKDF_V1) {
          thumbDek = await unwrapWithSharingKey(wrappedDek, getSharingPrivkey())
        } else if (upload.thumbnailDekFormat === ALG_PLOT_AES256GCM_V1 && plotId) {
          const plotKey = await loadPlotKey(plotId, apiKey)
          thumbDek = await unwrapDekWithPlotKey(wrappedDek, plotKey)
        } else {
          thumbDek = await unwrapDekWithMasterKey(wrappedDek, masterKey)
        }
        const r = await fetch(`${API_URL}/api/content/uploads/${upload.id}/thumb`, {
          headers: { 'X-Api-Key': apiKey },
        })
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        const encBytes = new Uint8Array(await r.arrayBuffer())
        const plainBytes = await decryptSymmetric(encBytes, thumbDek)
        const blob = new Blob([plainBytes], { type: 'image/jpeg' })
        const url = URL.createObjectURL(blob)
        if (!cancelled) {
          cacheThumbnail(upload.id, url)
          setSrc(url)
        } else {
          URL.revokeObjectURL(url)
        }
      } catch { /* show fallback */ }
      if (!cancelled) setLoading(false)
    })()
    return () => { cancelled = true }
  }, [upload.id, apiKey])

  if (loading) return <div className={`bg-forest-08 ${className ?? ''}`} style={style} />

  if (!src) {
    return (
      <div className={`bg-forest-08 flex items-center justify-center ${className ?? ''}`} style={style}>
        <OliveBranchIcon width={20} />
      </div>
    )
  }

  const rotateStyle = rotation ? { transform: `rotate(${rotation}deg)` } : {}
  return <img src={src} alt={alt} className={className} style={{ ...rotateStyle, ...style }} />
}
