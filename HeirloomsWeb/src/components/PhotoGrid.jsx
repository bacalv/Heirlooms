import { UploadThumb } from './UploadThumb'

function Thumb({ upload, selected, onSelect, linkTo }) {
  const inner = (
    <UploadThumb
      upload={upload}
      className="w-full h-full object-cover"
      rotation={upload.rotation}
      alt=""
    />
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

export function PhotoGrid({ uploads = [], selectable = false, selectedIds = new Set(), onSelect, getPhotoHref, cols = '3' }) {
  const colClass = cols === '4' ? 'grid-cols-3 sm:grid-cols-4' : cols === '5' ? 'grid-cols-3 sm:grid-cols-4 md:grid-cols-5' : 'grid-cols-2 sm:grid-cols-3'

  if (uploads.length === 0) return null

  return (
    <div className={`grid ${colClass} gap-2`}>
      {uploads.map((upload) => (
        <Thumb
          key={upload.id}
          upload={upload}
          selected={selectable ? selectedIds.has(upload.id) : false}
          onSelect={selectable ? onSelect : null}
          linkTo={getPhotoHref ? getPhotoHref(upload.id) : null}
        />
      ))}
    </div>
  )
}
