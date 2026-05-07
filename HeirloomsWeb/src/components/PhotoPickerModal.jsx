import { useState, useEffect, useCallback } from 'react'
import { BrandModal } from './BrandModal'
import { PhotoGrid } from './PhotoGrid'
import { useAuth } from '../AuthContext'
import { apiFetch } from '../api'

export function PhotoPickerModal({ initialSelectedIds = [], onDone, onCancel }) {
  const { apiKey } = useAuth()
  const [uploads, setUploads] = useState([])
  const [loading, setLoading] = useState(true)
  const [selectedIds, setSelectedIds] = useState(new Set(initialSelectedIds))
  const [activeTag, setActiveTag] = useState(null)

  useEffect(() => {
    apiFetch('/api/content/uploads', apiKey)
      .then((r) => r.ok ? r.json() : Promise.reject())
      .then((data) => { setUploads(data); setLoading(false) })
      .catch(() => setLoading(false))
  }, [apiKey])

  const allTags = [...new Set(uploads.flatMap((u) => u.tags ?? []))].sort()

  const visible = activeTag
    ? uploads.filter((u) => (u.tags ?? []).includes(activeTag))
    : uploads

  function toggleSelect(id) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const count = selectedIds.size

  return (
    <BrandModal onClose={onCancel} width="max-w-4xl">
      <div className="flex flex-col max-h-[80vh]">
        <div className="flex items-center justify-between p-4 border-b border-forest-15">
          <h2 className="text-base font-sans font-medium text-forest">Choose what to include</h2>
          <button
            onClick={onCancel}
            className="text-text-muted hover:text-forest text-xl leading-none"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        {allTags.length > 0 && (
          <div className="px-4 pt-3 flex flex-wrap gap-2">
            <button
              onClick={() => setActiveTag(null)}
              className={`px-3 py-1 rounded-chip text-xs border transition-colors ${
                activeTag === null
                  ? 'bg-forest text-parchment border-forest'
                  : 'bg-parchment text-forest border-forest-15 hover:border-forest'
              }`}
            >
              All
            </button>
            {allTags.map((tag) => (
              <button
                key={tag}
                onClick={() => setActiveTag(activeTag === tag ? null : tag)}
                className={`px-3 py-1 rounded-chip text-xs border transition-colors ${
                  activeTag === tag
                    ? 'bg-forest text-parchment border-forest'
                    : 'bg-parchment text-forest border-forest-15 hover:border-forest'
                }`}
              >
                {tag}
              </button>
            ))}
          </div>
        )}

        <div className="flex-1 overflow-y-auto p-4">
          {loading ? (
            <div className="flex justify-center py-10 text-text-muted text-sm">Loading…</div>
          ) : visible.length === 0 ? (
            <p className="text-center text-text-muted text-sm py-10">No items to show.</p>
          ) : (
            <PhotoGrid
              uploads={visible}
              selectable
              selectedIds={selectedIds}
              onSelect={toggleSelect}
              cols="5"
            />
          )}
        </div>

        <div className="flex items-center justify-between p-4 border-t border-forest-15">
          <span className="text-sm text-text-muted">{count} {count === 1 ? 'item' : 'items'} selected</span>
          <div className="flex gap-3">
            <button
              onClick={onCancel}
              className="px-4 py-2 rounded-button text-sm border border-forest-25 text-forest hover:border-forest transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={() => onDone([...selectedIds])}
              className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity"
            >
              Done ({count})
            </button>
          </div>
        </div>
      </div>
    </BrandModal>
  )
}
