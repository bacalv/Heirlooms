import { useState, useEffect, useRef } from 'react'
import { useAuth } from '../AuthContext'
import { API_URL } from '../api'

function Thumb({ upload, apiKey, selected, onSelect, linkTo }) {
  const fileUrl = `${API_URL}/api/content/uploads/${upload.id}/file`
  const displayUrl = upload.thumbnailKey
    ? `${API_URL}/api/content/uploads/${upload.id}/thumb`
    : fileUrl
  const needsFetch = upload.mimeType?.startsWith('image/') || !!upload.thumbnailKey
  const [blobUrl, setBlobUrl] = useState(null)
  const blobRef = useRef(null)

  useEffect(() => {
    if (!needsFetch) return
    fetch(displayUrl, { headers: { 'X-Api-Key': apiKey } })
      .then((r) => r.ok ? r.blob() : Promise.reject())
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        blobRef.current = url
        setBlobUrl(url)
      })
      .catch(() => {})
    return () => {
      if (blobRef.current) URL.revokeObjectURL(blobRef.current)
    }
  }, [displayUrl, apiKey, needsFetch])

  const inner = blobUrl ? (
    <img src={blobUrl} alt="" className="w-full h-full object-cover" />
  ) : (
    <div className="w-full h-full bg-forest-08 flex items-center justify-center">
      <span className="text-text-muted text-xs">…</span>
    </div>
  )

  if (onSelect) {
    return (
      <button
        type="button"
        onClick={() => onSelect(upload.id)}
        className={`relative aspect-square overflow-hidden rounded border-2 transition-all ${
          selected ? 'border-bloom brightness-90' : 'border-transparent'
        }`}
      >
        {inner}
        {selected && (
          <span className="absolute top-1 right-1 w-5 h-5 rounded-full bg-bloom flex items-center justify-center">
            <svg width="10" height="8" viewBox="0 0 10 8" fill="none" aria-hidden="true">
              <path d="M1 4L3.5 6.5L9 1" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
        )}
      </button>
    )
  }

  if (linkTo) {
    return (
      <a href={linkTo} className="relative aspect-square overflow-hidden rounded block border border-forest-08 hover:opacity-90 transition-opacity">
        {inner}
      </a>
    )
  }

  return (
    <div className="relative aspect-square overflow-hidden rounded border border-forest-08">
      {inner}
    </div>
  )
}

export function PhotoGrid({ uploads = [], apiKey: keyProp, selectable = false, selectedIds = new Set(), onSelect, getPhotoHref, cols = '3' }) {
  const { apiKey: ctxKey } = useAuth()
  const apiKey = keyProp ?? ctxKey
  const colClass = cols === '4' ? 'grid-cols-3 sm:grid-cols-4' : cols === '5' ? 'grid-cols-3 sm:grid-cols-4 md:grid-cols-5' : 'grid-cols-2 sm:grid-cols-3'

  if (uploads.length === 0) return null

  return (
    <div className={`grid ${colClass} gap-2`}>
      {uploads.map((upload) => (
        <Thumb
          key={upload.id}
          upload={upload}
          apiKey={apiKey}
          selected={selectable ? selectedIds.has(upload.id) : false}
          onSelect={selectable ? onSelect : null}
          linkTo={getPhotoHref ? getPhotoHref(upload.id) : null}
        />
      ))}
    </div>
  )
}
