import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthContext } from '../AuthContext'
import { PhotoDetailPage } from '../pages/PhotoDetailPage'

const mockUpload = {
  id: 'upload-abc',
  storageKey: 'photo.jpg',
  mimeType: 'image/jpeg',
  fileSize: 2048,
  uploadedAt: '2026-05-01T10:00:00Z',
  takenAt: '2026-04-28T08:00:00Z',
  latitude: 51.5074,
  longitude: -0.1278,
  rotation: 0,
  thumbnailKey: null,
  tags: ['family'],
  compostedAt: null,
}

function renderDetail(search = '') {
  return render(
    <AuthContext.Provider value={{ apiKey: 'test-key', onSignOut: vi.fn() }}>
      <MemoryRouter initialEntries={[`/photos/upload-abc${search}`]}>
        <Routes>
          <Route path="/photos/:id" element={<PhotoDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

describe('PhotoDetailPage', () => {
  beforeEach(() => {
    global.fetch = vi.fn().mockImplementation((url) => {
      const u = typeof url === 'string' ? url : url.toString()
      if (u.includes('/capsules')) return Promise.resolve({ ok: true, json: () => Promise.resolve({ capsules: [] }) })
      if (u.includes('/view')) return Promise.resolve({ ok: true })
      if (u.includes('/api/content/uploads/upload-abc')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockUpload) })
      }
      return Promise.resolve({ ok: false })
    })
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('Garden flavour (default) shows action affordances prominently', async () => {
    renderDetail()
    await waitFor(() => expect(screen.getByText(/Add this to a capsule/i)).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /Compost/i })).toBeInTheDocument()
  })

  it('Garden flavour back link points to Garden', async () => {
    renderDetail('?from=garden')
    await waitFor(() => expect(screen.getByText(/← Garden/i)).toBeInTheDocument())
  })

  it('Explore flavour shows metadata prominently and kebab menu', async () => {
    renderDetail('?from=explore')
    await waitFor(() => expect(screen.getByText(/← Explore/i)).toBeInTheDocument())
    // Location should be visible
    await waitFor(() => expect(screen.getByText(/51\.50/)).toBeInTheDocument())
    // Kebab menu button should exist instead of inline Compost button
    expect(screen.queryByRole('button', { name: /Compost/i })).not.toBeInTheDocument()
    expect(screen.getByTitle('Actions')).toBeInTheDocument()
  })

  it('no ?from param defaults to Garden flavour', async () => {
    renderDetail()
    await waitFor(() => expect(screen.getByText(/← Garden/i)).toBeInTheDocument())
  })
})
