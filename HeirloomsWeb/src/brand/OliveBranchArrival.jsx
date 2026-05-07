import { useEffect, useRef } from 'react'
import { lerp, interpolateHexColour, prefersReducedMotion } from './animations'

const DURATION_MS = 3000
const FOREST = '#3F4F33'
const BLOOM = '#D89B85'

/**
 * Arrival animation: branch draws, leaves emerge in pairs base-to-tip,
 * olive forms in forest then ripens to bloom, optional wordmark settles.
 * Honours `prefers-reduced-motion` by snapping to the end state.
 */
export function OliveBranchArrival({
  onComplete,
  withWordmark = true,
  width = 130,
  className,
  style,
}) {
  const svgRef = useRef(null)

  useEffect(() => {
    const svg = svgRef.current
    if (!svg) return

    // Measure actual path length so the dasharray matches the Bézier curve.
    const branch = svg.querySelector('.ob-branch')
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
      <path
        className="ob-branch"
        d="M 70,140 C 73,118 67,88 70,58"
        stroke="var(--hl-forest)"
        strokeWidth="1.7"
        fill="none"
        strokeLinecap="round"
      />
      {/* Leaves — scale and opacity animated via style; transform-box: fill-box
          ensures transformOrigin resolves against the element, not the viewport. */}
      <ellipse className="ob-leaf ob-leaf-1l" cx="56" cy="124" rx="9.5" ry="2.4" fill="var(--hl-forest)" transform="rotate(18 67 122)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'right center' }} />
      <ellipse className="ob-leaf ob-leaf-1r" cx="84" cy="124" rx="9.5" ry="2.4" fill="var(--hl-forest)" transform="rotate(-18 73 122)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'left center' }} />
      <ellipse className="ob-leaf ob-leaf-2l" cx="55" cy="103" rx="8.5" ry="2.2" fill="var(--hl-forest)" transform="rotate(22 64 101)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'right center' }} />
      <ellipse className="ob-leaf ob-leaf-2r" cx="85" cy="103" rx="8.5" ry="2.2" fill="var(--hl-forest)" transform="rotate(-22 76 101)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'left center' }} />
      <ellipse className="ob-leaf ob-leaf-3l" cx="58" cy="82"  rx="7"   ry="1.9" fill="var(--hl-forest)" transform="rotate(26 65 80)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'right center' }} />
      <ellipse className="ob-leaf ob-leaf-3r" cx="82" cy="82"  rx="7"   ry="1.9" fill="var(--hl-forest)" transform="rotate(-26 75 80)"
        style={{ transform: 'scale(0)', opacity: 0, transformBox: 'fill-box', transformOrigin: 'left center' }} />
      <ellipse className="ob-olive" cx="70" cy="55" rx="0" ry="0" fill={FOREST} />
      {withWordmark && (
        <text
          className="ob-wordmark"
          x="70" y="182"
          fontFamily="var(--hl-font-serif)"
          fontSize="20"
          fontStyle="italic"
          textAnchor="middle"
          fill="var(--hl-forest)"
          opacity="0"
        >
          Heirlooms
        </text>
      )}
    </svg>
  )
}

function applyProgress(svg, t) {
  const branch = svg.querySelector('.ob-branch')
  if (branch) {
    const len = parseFloat(branch.getAttribute('stroke-dasharray') || '0')
    branch.style.strokeDashoffset = String(len * (1 - lerp(t, 0, 0.30)))
  }

  setLeaf(svg, '.ob-leaf-1l', lerp(t, 0.22, 0.42))
  setLeaf(svg, '.ob-leaf-1r', lerp(t, 0.22, 0.42))
  setLeaf(svg, '.ob-leaf-2l', lerp(t, 0.38, 0.58))
  setLeaf(svg, '.ob-leaf-2r', lerp(t, 0.38, 0.58))
  setLeaf(svg, '.ob-leaf-3l', lerp(t, 0.54, 0.72))
  setLeaf(svg, '.ob-leaf-3r', lerp(t, 0.54, 0.72))

  const olive = svg.querySelector('.ob-olive')
  if (olive) {
    if (t < 0.70) {
      olive.setAttribute('rx', '0')
      olive.setAttribute('ry', '0')
      olive.setAttribute('fill', FOREST)
    } else if (t < 0.84) {
      const op = lerp(t, 0.70, 0.84)
      olive.setAttribute('rx', String(3.5 * op))
      olive.setAttribute('ry', String(5.5 * op))
      olive.setAttribute('fill', FOREST)
    } else if (t < 0.92) {
      olive.setAttribute('rx', '3.5')
      olive.setAttribute('ry', '5.5')
      olive.setAttribute('fill', interpolateHexColour(FOREST, BLOOM, lerp(t, 0.84, 0.92)))
    } else {
      olive.setAttribute('rx', '3.5')
      olive.setAttribute('ry', '5.5')
      olive.setAttribute('fill', BLOOM)
    }
  }

  const wordmark = svg.querySelector('.ob-wordmark')
  if (wordmark) {
    wordmark.style.opacity = String(lerp(t, 0.88, 1.0))
  }
}

function setLeaf(svg, selector, progress) {
  const leaf = svg.querySelector(selector)
  if (!leaf) return
  leaf.style.transform = `scale(${progress})`
  leaf.style.opacity = String(progress)
}
