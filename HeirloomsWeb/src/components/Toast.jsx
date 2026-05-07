import { useEffect } from 'react'

export function Toast({ message, onDismiss }) {
  useEffect(() => {
    const t = setTimeout(onDismiss, 3000)
    return () => clearTimeout(t)
  }, [onDismiss])

  return (
    <div
      className="fixed top-20 left-1/2 -translate-x-1/2 z-50 cursor-pointer animate-[toast-in_200ms_ease-out]"
      onClick={onDismiss}
    >
      <div className="bg-parchment border border-earth/30 shadow-lg rounded-card px-5 py-3 font-serif italic text-forest text-sm whitespace-nowrap">
        {message}
      </div>
    </div>
  )
}
