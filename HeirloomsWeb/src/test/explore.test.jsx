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

// URL-routing mock: TagChromePicker now fetches /uploads/tags on mount.
// Using mockImplementation so all tests handle the tags fetch regardless of order.
function setupFetch({ items = [], nextCursor = null, extraItems = null } = {}) {
  let uploadsCallCount = 0
  global.fetch.mockImplementation((url) => {
    const u = typeof url === 'string' ? url : url.toString()
    if (u.includes('/uploads/tags')) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve([]) })
    }
    if (u.includes('/api/content/uploads') && !u.includes('/thumb') && !u.includes('/file')) {
      uploadsCallCount++
      if (extraItems && uploadsCallCount > 1) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ items: extraItems, next_cursor: null }) })
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve({ items, next_cursor: nextCursor }) })
    }
    return Promise.resolve({ ok: false }) // thumbnails etc.
  })
}

describe('ExplorePage', () => {
  beforeEach(() => { global.fetch = vi.fn() })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders page heading', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.getByRole('heading', { name: /Explore/i })).toBeInTheDocument())
  })

  it('renders photo grid when items are present', async () => {
    setupFetch({ items: [mockUpload(), mockUpload()] })
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.queryByText('Loading…')).not.toBeInTheDocument())
    expect(document.querySelectorAll('a[href^="/photos/"]').length).toBeGreaterThan(0)
  })

  it('shows empty state when no items', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.getByText(/Nothing here yet/)).toBeInTheDocument())
  })

  it('shows Load more button when next_cursor is present', async () => {
    setupFetch({ items: [mockUpload()], nextCursor: 'cursor-abc' })
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.getByRole('button', { name: /Load more/i })).toBeInTheDocument())
  })

  it('hides Load more button when next_cursor is null', async () => {
    setupFetch({ items: [mockUpload()] })
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => expect(screen.queryByText('Load more')).not.toBeInTheDocument())
  })

  it('Load more appends next page of items', async () => {
    setupFetch({ items: [mockUpload({ id: 'u1' })], nextCursor: 'cursor-1', extraItems: [mockUpload({ id: 'u2' })] })
    render(<Wrapper><ExplorePage /></Wrapper>)
    const btn = await waitFor(() => screen.getByRole('button', { name: /Load more/i }))
    fireEvent.click(btn)
    await waitFor(() => expect(screen.queryByRole('button', { name: /Load more/i })).not.toBeInTheDocument())
  })

  // ---- Filter chrome tests -------------------------------------------------

  it('renders sort dropdown with default option', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByRole('combobox'))
    expect(screen.getByRole('combobox').value).toBe('upload_newest')
  })

  it('changing sort triggers a re-fetch', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByRole('combobox'))
    act(() => { fireEvent.change(screen.getByRole('combobox'), { target: { value: 'upload_oldest' } }) })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('sort=upload_oldest'))).toBe(true)
    })
  })

  it('renders tag picker in filter chrome', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
  })

  it('typing a tag and pressing Enter triggers a re-fetch with tag param', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    act(() => {
      fireEvent.change(screen.getByPlaceholderText(/filter by tag/i), { target: { value: 'family' } })
      fireEvent.keyDown(screen.getByPlaceholderText(/filter by tag/i), { key: 'Enter' })
    })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('tag=family'))).toBe(true)
    })
  })

  it('adding two tags sends both in the request', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    const input = await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    // Add first tag
    act(() => { fireEvent.change(input, { target: { value: 'family' } }); fireEvent.keyDown(input, { key: 'Enter' }) })
    // After adding first tag, input placeholder becomes empty — find the same input element
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('tag=family'))).toBe(true)
    })
    // Add second tag using the same input ref
    act(() => { fireEvent.change(input, { target: { value: 'holiday' } }); fireEvent.keyDown(input, { key: 'Enter' }) })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('tag=family%2Choliday') || u.includes('tag=family,holiday'))).toBe(true)
    })
  })

  it('Save as plot bar appears when tags are active', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    expect(screen.queryByText(/Save as plot/i)).not.toBeInTheDocument()
    act(() => {
      fireEvent.change(screen.getByPlaceholderText(/filter by tag/i), { target: { value: 'family' } })
      fireEvent.keyDown(screen.getByPlaceholderText(/filter by tag/i), { key: 'Enter' })
    })
    await waitFor(() => expect(screen.getByText(/Save as plot/i)).toBeInTheDocument())
  })

  it('Save as plot sends tag_criteria array including all selected tags', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    const input = await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    // Add two tags
    act(() => { fireEvent.change(input, { target: { value: 'family' } }); fireEvent.keyDown(input, { key: 'Enter' }) })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('tag=family'))).toBe(true)
    })
    act(() => { fireEvent.change(input, { target: { value: 'holiday' } }); fireEvent.keyDown(input, { key: 'Enter' }) })
    await waitFor(() => {
      const calls = global.fetch.mock.calls.map((c) => c[0])
      expect(calls.some((u) => u.includes('holiday'))).toBe(true)
    })
    // Click Save as plot
    fireEvent.click(screen.getByText(/Save as plot/i))
    await waitFor(() => screen.getByPlaceholderText(/Plot name/i))
    fireEvent.change(screen.getByPlaceholderText(/Plot name/i), { target: { value: 'Family & Holidays' } })
    fireEvent.click(screen.getByRole('button', { name: /^Save$/ }))
    await waitFor(() => {
      const postCalls = global.fetch.mock.calls.filter((c) => c[1]?.method === 'POST' && c[0].includes('/api/plots'))
      expect(postCalls.length).toBeGreaterThan(0)
      const body = JSON.parse(postCalls[0][1].body)
      expect(body.tag_criteria).toContain('family')
      expect(body.tag_criteria).toContain('holiday')
      expect(body.name).toBe('Family & Holidays')
    })
  })

  it('Clear filters button appears when tags are active', async () => {
    setupFetch()
    render(<Wrapper><ExplorePage /></Wrapper>)
    await waitFor(() => screen.getByPlaceholderText(/filter by tag/i))
    expect(screen.queryByText('Clear filters')).not.toBeInTheDocument()
    act(() => {
      fireEvent.change(screen.getByPlaceholderText(/filter by tag/i), { target: { value: 'family' } })
      fireEvent.keyDown(screen.getByPlaceholderText(/filter by tag/i), { key: 'Enter' })
    })
    await waitFor(() => expect(screen.getByText('Clear filters')).toBeInTheDocument())
  })
})
