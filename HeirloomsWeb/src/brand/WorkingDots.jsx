export function WorkingDots({ label, size = 'md', className, style }) {
  const dotSize = { sm: 4, md: 5, lg: 7 }[size]
  const gap = { sm: 3, md: 4, lg: 5 }[size]

  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex flex-col items-center gap-2 ${className || ''}`}
      style={style}
    >
      <div className="flex" style={{ gap }}>
        <span className="hl-dot" style={{ width: dotSize, height: dotSize }} />
        <span className="hl-dot" style={{ width: dotSize, height: dotSize, animationDelay: '0.2s' }} />
        <span className="hl-dot" style={{ width: dotSize, height: dotSize, animationDelay: '0.4s' }} />
      </div>
      {label && <span className="text-[11px] text-text-muted font-serif italic">{label}</span>}
      <span className="sr-only">{label || 'Loading'}</span>
    </div>
  )
}
