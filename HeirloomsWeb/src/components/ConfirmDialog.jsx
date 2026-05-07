import { useEffect } from 'react'
import { BrandModal } from './BrandModal'

export function ConfirmDialog({
  title,
  titleItalic = false,
  body,
  primaryLabel,
  primaryClass = 'bg-forest text-parchment',
  cancelLabel,
  onConfirm,
  onCancel,
  focusPrimary = false,
  children,
}) {
  return (
    <BrandModal onClose={onCancel} width="max-w-md">
      <div className="p-6 space-y-4">
        <p className={`text-xl font-sans text-forest ${titleItalic ? 'font-serif italic' : ''}`}>
          {title}
        </p>
        {body && <p className="text-sm text-text-body">{body}</p>}
        {children}
        <div className="flex items-center justify-between pt-2">
          <button
            onClick={onCancel}
            autoFocus={!focusPrimary}
            className="px-4 py-2 rounded-button text-sm border border-forest-25 text-forest bg-parchment hover:border-forest transition-colors"
          >
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            autoFocus={focusPrimary}
            className={`px-4 py-2 rounded-button text-sm ${primaryClass} hover:opacity-90 transition-opacity`}
          >
            {primaryLabel}
          </button>
        </div>
      </div>
    </BrandModal>
  )
}
