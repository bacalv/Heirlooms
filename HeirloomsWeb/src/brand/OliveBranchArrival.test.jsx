import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { OliveBranchArrival } from './OliveBranchArrival'

describe('OliveBranchArrival', () => {
  beforeEach(() => {
    window.matchMedia = vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
  })

  it('renders the branch, six leaves, and the olive', () => {
    const { container } = render(<OliveBranchArrival />)
    expect(container.querySelector('.ob-branch')).toBeInTheDocument()
    expect(container.querySelectorAll('.ob-leaf')).toHaveLength(6)
    expect(container.querySelector('.ob-olive')).toBeInTheDocument()
  })

  it('renders the wordmark by default and omits it when withWordmark=false', () => {
    const { container, rerender } = render(<OliveBranchArrival />)
    expect(container.querySelector('.ob-wordmark')).toBeInTheDocument()
    rerender(<OliveBranchArrival withWordmark={false} />)
    expect(container.querySelector('.ob-wordmark')).not.toBeInTheDocument()
  })

  it('fires onComplete on next tick under reduced motion', async () => {
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    const onComplete = vi.fn()
    render(<OliveBranchArrival onComplete={onComplete} />)
    await waitFor(() => expect(onComplete).toHaveBeenCalledTimes(1))
  })
})
