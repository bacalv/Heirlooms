/**
 * Linear interpolation clamped to [0, 1] over a time range.
 * Returns 0 if t < start, 1 if t > end, otherwise the linear ratio.
 */
export function lerp(t, start, end) {
  return Math.max(0, Math.min(1, (t - start) / (end - start)))
}

/**
 * Interpolate between two #RRGGBB hex colours.
 * Returns an `rgb(r, g, b)` string.
 */
export function interpolateHexColour(a, b, t) {
  const ah = parseInt(a.slice(1), 16)
  const bh = parseInt(b.slice(1), 16)
  const ar = (ah >> 16) & 0xff
  const ag = (ah >> 8) & 0xff
  const ab = ah & 0xff
  const br = (bh >> 16) & 0xff
  const bg = (bh >> 8) & 0xff
  const bb = bh & 0xff
  const r = Math.round(ar + (br - ar) * t)
  const g = Math.round(ag + (bg - ag) * t)
  const blue = Math.round(ab + (bb - ab) * t)
  return `rgb(${r}, ${g}, ${blue})`
}

/**
 * Returns true if the user has requested reduced motion.
 * Defaults to false in environments without `matchMedia`.
 */
export function prefersReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}
