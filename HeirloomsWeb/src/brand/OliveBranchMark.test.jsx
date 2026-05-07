import { render, screen } from '@testing-library/react'
import { OliveBranchMark } from './OliveBranchMark'

describe('OliveBranchMark', () => {
  it('renders an SVG element', () => {
    const { container } = render(<OliveBranchMark />)
    expect(container.querySelector('svg')).toBeInTheDocument()
  })

  it('uses forest colour for apex olive when state="forest"', () => {
    const { container } = render(<OliveBranchMark state="forest" />)
    const ellipses = container.querySelectorAll('ellipse')
    const apex = ellipses[ellipses.length - 1]
    expect(apex.getAttribute('fill')).toBe('var(--hl-forest)')
  })

  it('uses bloom colour for apex olive when state="bloomed"', () => {
    const { container } = render(<OliveBranchMark state="bloomed" />)
    const ellipses = container.querySelectorAll('ellipse')
    const apex = ellipses[ellipses.length - 1]
    expect(apex.getAttribute('fill')).toBe('var(--hl-bloom)')
  })
})
