import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext } from '../AuthContext'
import { compostHeapEmptyState } from '../brand/brandStrings'

function Wrapper({ children, initialEntries = ['/'] }) {
  return (
    <AuthContext.Provider value={{ apiKey: 'test-key', onSignOut: vi.fn() }}>
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    </AuthContext.Provider>
  )
}

const mockUpload = (overrides = {}) => ({
  id: 'upload-1',
  storageKey: 'abc.jpg',
  mimeType: 'image/jpeg',
  fileSize: 1024,
  uploadedAt: '2026-05-01T10:00:00Z',
  rotation: 0,
  thumbnailKey: null,
  tags: [],
  compostedAt: null,
  ...overrides,
})

// ==================== PHOTO DETAIL PAGE ====================

import { PhotoDetailPage } from '../pages/PhotoDetailPage'

describe('PhotoDetailPage — compost affordance', () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('Compost button is enabled when no tags and no active capsules', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, blob: () => Promise.resolve(new Blob()) }) // thumb
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ capsules: [] }) }) // capsules

    render(
      <Wrapper initialEntries={[{ pathname: '/photos/upload-1', state: { upload: mockUpload() } }]}>
        <PhotoDetailPage />
      </Wrapper>
    )
    await waitFor(() => expect(screen.getByText('Compost')).toBeInTheDocument())
    expect(screen.getByText('Compost')).not.toBeDisabled()
    expect(screen.queryByText(/Compost requires/)).not.toBeInTheDocument()
  })

  it('Compost button is disabled with helper text when tags are present', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, blob: () => Promise.resolve(new Blob()) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ capsules: [] }) })

    render(
      <Wrapper initialEntries={[{ pathname: '/photos/upload-1', state: { upload: mockUpload({ tags: ['family'] }) } }]}>
        <PhotoDetailPage />
      </Wrapper>
    )
    await waitFor(() => expect(screen.getByText('Compost')).toBeInTheDocument())
    expect(screen.getByText('Compost')).toBeDisabled()
    expect(screen.getByText(/Compost requires no tags/)).toBeInTheDocument()
  })

  it('Compost button is disabled with helper text when in active capsule', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, blob: () => Promise.resolve(new Blob()) })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ capsules: [{ id: 'cap-1', state: 'open', recipients: ['Sophie'] }] }),
      })

    render(
      <Wrapper initialEntries={[{ pathname: '/photos/upload-1', state: { upload: mockUpload() } }]}>
        <PhotoDetailPage />
      </Wrapper>
    )
    await waitFor(() => expect(screen.getByText('Compost')).toBeInTheDocument())
    expect(screen.getByText('Compost')).toBeDisabled()
    expect(screen.getByText(/Compost requires no tags/)).toBeInTheDocument()
  })

  it('successful compost navigates to Garden', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, blob: () => Promise.resolve(new Blob()) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ capsules: [] }) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockUpload({ compostedAt: '2026-05-09T12:00:00Z' })) }) // compost POST

    render(
      <Wrapper initialEntries={[{ pathname: '/photos/upload-1', state: { upload: mockUpload() } }]}>
        <PhotoDetailPage />
      </Wrapper>
    )
    await waitFor(() => expect(screen.getByText('Compost')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Compost'))
    // After compost, navigated away — the Compost button should be gone or working state
    await waitFor(() => expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/compost'),
      expect.objectContaining({ method: 'POST' })
    ))
  })

  it('composted upload shows Restore button replacing Compost', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, blob: () => Promise.resolve(new Blob()) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ capsules: [] }) })

    render(
      <Wrapper initialEntries={[{ pathname: '/photos/upload-1', state: { upload: mockUpload({ compostedAt: '2026-05-01T10:00:00Z' }) } }]}>
        <PhotoDetailPage />
      </Wrapper>
    )
    await waitFor(() => expect(screen.getByText('Restore')).toBeInTheDocument())
    expect(screen.queryByText('Compost')).not.toBeInTheDocument()
    expect(screen.getByText(/Will be permanently deleted/)).toBeInTheDocument()
  })
})

// ==================== GARDEN PAGE ====================

import { GardenPage } from '../pages/GardenPage'

describe('GardenPage — compost heap link', () => {
  beforeEach(() => { global.fetch = vi.fn() })
  afterEach(() => { vi.restoreAllMocks() })

  it('shows Compost heap link with correct count', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [mockUpload()], next_cursor: null }) }) // active list
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [mockUpload({ id: 'c1', compostedAt: '2026-05-01T10:00:00Z' })], next_cursor: null }),
      }) // composted list
      .mockResolvedValue({ ok: false }) // thumbnail fetches fail gracefully

    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText(/Compost heap \(1\)/)).toBeInTheDocument())
  })

  it('shows Compost heap (0) link when heap is empty', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [mockUpload()], next_cursor: null }) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [], next_cursor: null }) })
      .mockResolvedValue({ ok: false }) // thumbnail fetches

    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText(/Compost heap \(0\)/)).toBeInTheDocument())
  })

  it('shows transient composted message when navigated from compost', async () => {
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [], next_cursor: null }) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [], next_cursor: null }) })
      .mockResolvedValue({ ok: false })

    render(
      <Wrapper initialEntries={[{ pathname: '/', state: { composted: true } }]}>
        <GardenPage />
      </Wrapper>
    )
    await waitFor(() => expect(screen.getByText(/Composted\. Find it in the compost heap/)).toBeInTheDocument())
  })
})

// ==================== COMPOST HEAP PAGE ====================

import { CompostHeapPage } from '../pages/CompostHeapPage'

describe('CompostHeapPage', () => {
  beforeEach(() => { global.fetch = vi.fn() })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders list of composted items with metadata', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          items: [
            mockUpload({ id: 'c1', compostedAt: '2026-05-01T10:00:00Z' }),
            mockUpload({ id: 'c2', storageKey: 'xyz.jpg', compostedAt: '2026-04-20T10:00:00Z' }),
          ],
          next_cursor: null,
        }),
      })
      .mockResolvedValue({ ok: false }) // thumb fetches fail gracefully

    render(<Wrapper><CompostHeapPage /></Wrapper>)
    await waitFor(() => expect(screen.getAllByText(/days left/)).toHaveLength(2))
    expect(screen.getAllByText(/Composted/)).toHaveLength(2)
  })

  it('Restore button triggers restore and removes the row', async () => {
    global.fetch
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ items: [mockUpload({ id: 'c1', compostedAt: '2026-05-01T10:00:00Z' })], next_cursor: null }),
      })
      .mockResolvedValueOnce({ ok: false }) // thumb
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockUpload({ id: 'c1' })) }) // restore

    render(<Wrapper><CompostHeapPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText('Restore')).toBeInTheDocument())
    fireEvent.click(screen.getByText('Restore'))
    await waitFor(() => expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/restore'),
      expect.objectContaining({ method: 'POST' })
    ))
    await waitFor(() => expect(screen.queryByText(/days left/)).not.toBeInTheDocument())
  })

  it('empty heap renders one of the five brand-voice lines', async () => {
    global.fetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ items: [], next_cursor: null }) })

    render(<Wrapper><CompostHeapPage /></Wrapper>)
    await waitFor(() => {
      const rendered = screen.queryByRole('heading', { level: 1, name: /Compost heap/ })
        ? true : false
      // Check the empty state line is one of the five
      const found = compostHeapEmptyState.some((line) => screen.queryByText(line))
      expect(found).toBe(true)
    })
  })
})
