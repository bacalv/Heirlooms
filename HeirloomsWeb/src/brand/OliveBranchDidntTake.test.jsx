import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { OliveBranchDidntTake } from './OliveBranchDidntTake'

describe('OliveBranchDidntTake', () => {
  beforeEach(() => {
    window.matchMedia = vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
  })

  it("renders the partial branch, leaf pair, seed, and didn't-take text", () => {
    const { container, getByText } = render(<OliveBranchDidntTake />)
    expect(container.querySelector('.dt-branch')).toBeInTheDocument()
    expect(container.querySelectorAll('.dt-leaf')).toHaveLength(2)
    expect(container.querySelector('.dt-seed')).toBeInTheDocument()
    expect(getByText("didn't take")).toBeInTheDocument()
  })

  it('fires onComplete on next tick under reduced motion', async () => {
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    const onComplete = vi.fn()
    render(<OliveBranchDidntTake onComplete={onComplete} />)
    await waitFor(() => expect(onComplete).toHaveBeenCalledTimes(1))
  })
})
