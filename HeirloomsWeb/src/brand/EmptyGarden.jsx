import { OliveBranchMark } from './OliveBranchMark'

export function EmptyGarden({ onUpload }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-6 text-center">
      <OliveBranchMark width={64} state="bloomed" />
      <p className="font-serif italic text-forest text-[18px] mt-6">
        "A garden begins with a single seed."
      </p>
      <p className="text-text-muted text-[13px] mt-2 max-w-xs">
        Drag a photo here, or share from your phone.
      </p>
      {onUpload && (
        <button
          onClick={onUpload}
          className="mt-6 px-4 py-2 bg-forest text-parchment rounded-button font-serif italic text-[13px]"
        >
          plant your first
        </button>
      )}
    </div>
  )
}
