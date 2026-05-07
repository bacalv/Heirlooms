import { useEffect, useRef } from 'react'
import { lerp, prefersReducedMotion } from './animations'

const DURATION_MS = 2000

/**
 * Didn't-take animation: partial branch with one leaf pair, a pause beat,
 * earth-coloured seed appears on the soil line, "didn't take" text fades in.
 * Honours `prefers-reduced-motion` by snapping to the end state.
 */
export function OliveBranchDidntTake({
  onComplete,
  width = 130,
  className,
  style,
}) {
  const svgRef = useRef(null)

  useEffect(() => {
    const svg = svgRef.current
    if (!svg) return

    const branch = svg.querySelector('.dt-branch')
    if (branch) {
      const len = branch.getTotalLength()
      branch.setAttribute('stroke-dasharray', String(len))
      branch.style.strokeDashoffset = String(len)
    }

    if (prefersReducedMotion()) {
      applyProgress(svg, 1)
      const t = setTimeout(() => onComplete?.(), 0)
      return () => clearTimeout(t)
    }

    const start = performance.now()
    let raf = 0
    const tick = (now) => {
      const t = Math.min(1, (now - start) / DURATION_MS)
      applyProgress(svg, t)
      if (t < 1) {
        raf = requestAnimationFrame(tick)
      } else {
        onComplete?.()
      }
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [onComplete])

  return (
    <svg
      ref={svgRef}
      viewBox="0 0 140 200"
      width={width}
      height={Math.round(width * (200 / 140))}
      className={className}
      style={style}
      aria-hidden="true"
    >
      {/* Soil line */}
      <line x1="20" y1="142" x2="120" y2="142"
        stroke="var(--hl-forest)" strokeWidth="0.4"
        strokeDasharray="2 2" opacity="0.3" />
      <path
        className="dt-branch"
        d="M 70,140 C 73,128 69,118 71,110"
        stroke="var(--hl-forest)"
        strokeWidth="1.7"
        fill="none"
        strokeLinecap="round"
      />
      <ellipse className="dt-leaf dt-leaf-1l" cx="56" cy="124" rx="9.5" ry="2.4" fill="var(--hl-forest)" transform="rotate(18 67 122)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'right center' }} />
      <ellipse className="dt-leaf dt-leaf-1r" cx="84" cy="124" rx="9.5" ry="2.4" fill="var(--hl-forest)" transform="rotate(-18 73 122)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'left center' }} />
      <ellipse className="dt-seed" cx="80" cy="148" rx="0" ry="0" fill="var(--hl-earth)" transform="rotate(15 80 148)" />
      <text
        className="dt-text"
        x="70" y="182"
        fontFamily="var(--hl-font-serif)"
        fontSize="16"
        fontStyle="italic"
        textAnchor="middle"
        fill="var(--hl-earth)"
        opacity="0"
      >
        didn't take
      </text>
    </svg>
  )
}

function applyProgress(svg, t) {
  const branch = svg.querySelector('.dt-branch')
  if (branch) {
    const len = parseFloat(branch.getAttribute('stroke-dasharray') || '0')
    branch.style.strokeDashoffset = String(len * (1 - lerp(t, 0, 0.25)))
  }

  setLeaf(svg, '.dt-leaf-1l', lerp(t, 0.20, 0.45))
  setLeaf(svg, '.dt-leaf-1r', lerp(t, 0.20, 0.45))

  const seed = svg.querySelector('.dt-seed')
  if (seed) {
    const sp = lerp(t, 0.55, 0.80)
    seed.setAttribute('rx', String(3 * sp))
    seed.setAttribute('ry', String(2 * sp))
  }

  const text = svg.querySelector('.dt-text')
  if (text) {
    text.style.opacity = String(lerp(t, 0.75, 1.0))
  }
}

function setLeaf(svg, selector, progress) {
  const leaf = svg.querySelector(selector)
  if (!leaf) return
  leaf.style.transform = `scale(${progress})`
  leaf.style.opacity = String(progress)
}
