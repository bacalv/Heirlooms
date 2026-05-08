import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
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

  // ---- Filter chrome tests -------------------------------------------------

  it('renders sort dropdown with default option', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ items: [], next_cursor: null }),
    })
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByRole('combobox'))
    const select = screen.getByRole('combobox')
    expect(select.value).toBe('upload_newest')
  })

  it('changing sort triggers a re-fetch', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [], next_cursor: null }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [], next_cursor: null }),
      })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByRole('combobox'))
    act(() => { fireEvent.change(screen.getByRole('combobox'), { target: { value: 'upload_oldest' } }) })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('sort=upload_oldest'))).toBe(true)
    })
  })

  it('renders tag input in filter chrome', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ items: [], next_cursor: null }),
    })
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
  })

  it('entering a tag value triggers a re-fetch with tag param', async () => {
    global.fetch
      .mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ items: [], next_cursor: null }),
      })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    act(() => {
      fireEvent.change(screen.getByPlaceholderText(/filter by tag/i), { target: { value: 'family' } })
    })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('tag=family'))).toBe(true)
    })
  })

  it('Clear filters button appears when filters are active', async () => {
    global.fetch
      .mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ items: [], next_cursor: null }),
      })

    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    expect(screen.queryByText('Clear filters')).not.toBeInTheDocument()

    act(() => {
      fireEvent.change(screen.getByPlaceholderText(/filter by tag/i), { target: { value: 'family' } })
    })
    await waitFor(() => expect(screen.getByText('Clear filters')).toBeInTheDocument())
  })
})
