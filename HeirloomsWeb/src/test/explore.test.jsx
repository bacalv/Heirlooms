import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext } from '../AuthContext'
import { ExplorePage } from '../pages/ExplorePage'

function Wrapper({ children }) {
  return (
    <AuthContext.Provider value={{ apiKey: 'test-key', onSignOut: vi.fn() }}>
      <MemoryRouter>{children}</MemoryRouter>
    </AuthContext.Provider>
  )
}

const mockUpload = (overrides = {}) => ({
  id: `upload-${Math.random()}`,
  storageKey: 'photo.jpg',
  mimeType: 'image/jpeg',
  fileSize: 1024,
  uploadedAt: '2026-05-01T10:00:00Z',
  rotation: 0,
  thumbnailKey: null,
  tags: [],
  compostedAt: null,
  ...overrides,
})

describe('ExplorePage', () => {
  beforeEach(() => { global.fetch = vi.fn() })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders page heading', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ items: [], next_cursor: null }),
    })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.getByRole('heading', { name: /Explore/i })).toBeInTheDocument())
  })

  it('renders photo grid when items are present', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [mockUpload(), mockUpload()], next_cursor: null }),
      })
      .mockResolvedValue({ ok: false }) // thumbnail fetches

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.queryByText('Loading…')).not.toBeInTheDocument())
    // PhotoGrid renders at least one anchor per item (linkTo prop)
    expect(document.querySelectorAll('a[href^="/photos/"]').length).toBeGreaterThan(0)
  })

  it('shows empty state when no items', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ items: [], next_cursor: null }),
    })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.getByText(/Nothing here yet/)).toBeInTheDocument())
  })

  it('shows Load more button when next_cursor is present', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [mockUpload()], next_cursor: 'cursor-abc' }),
      })
      .mockResolvedValue({ ok: false })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.getByRole('button', { name: /Load more/i })).toBeInTheDocument())
  })

  it('hides Load more button when next_cursor is null', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [mockUpload()], next_cursor: null }),
      })
      .mockResolvedValue({ ok: false })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.queryByText('Load more')).not.toBeInTheDocument())
  })

  it('Load more appends next page of items', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [mockUpload({ id: 'u1' })], next_cursor: 'cursor-1' }),
      })
      .mockResolvedValue({ ok: false }) // thumbnails for first page
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ items: [mockUpload({ id: 'u2' })], next_cursor: null }),
    })

    render(<Wrapper><ExplorePage /></Wrapper>)
    const btn = await waitFor(() => screen.getByRole('button', { name: /Load more/i }))
    fireEvent.click(btn)

    await waitFor(() => expect(screen.queryByRole('button', { name: /Load more/i })).not.toBeInTheDocument())
  })
})
