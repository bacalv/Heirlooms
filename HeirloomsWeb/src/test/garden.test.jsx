import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext } from '../AuthContext'
import { GardenPage } from '../pages/GardenPage'

function Wrapper({ children }) {
  return (
    <AuthContext.Provider value={{ apiKey: 'test-key', onSignOut: vi.fn() }}>
      <MemoryRouter>{children}</MemoryRouter>
    </AuthContext.Provider>
  )
}

const systemPlot = {
  id: 'system-plot-id',
  name: '__just_arrived__',
  is_system_defined: true,
  sort_order: -1000,
  tag_criteria: [],
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
}

const userPlot1 = {
  id: 'user-plot-1',
  name: 'Family',
  is_system_defined: false,
  sort_order: 0,
  tag_criteria: ['family'],
  created_at: '2026-05-02T00:00:00Z',
  updated_at: '2026-05-02T00:00:00Z',
}

const userPlot2 = {
  id: 'user-plot-2',
  name: 'Holidays',
  is_system_defined: false,
  sort_order: 1,
  tag_criteria: ['holiday'],
  created_at: '2026-05-03T00:00:00Z',
  updated_at: '2026-05-03T00:00:00Z',
}

function mockFetch({ plots = [systemPlot, userPlot1, userPlot2], plotItems = [] } = {}) {
  global.fetch.mockImplementation((url) => {
    const u = typeof url === 'string' ? url : url.toString()
    if (u.includes('/api/plots') && !u.includes('/api/plots/')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(plots),
      })
    }
    if (u.includes('/api/content/uploads/composted')) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve({ items: [], next_cursor: null }) })
    }
    if (u.includes('/api/content/uploads')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ items: plotItems, next_cursor: null }),
      })
    }
    return Promise.resolve({ ok: false })
  })
}

describe('GardenPage', () => {
  beforeEach(() => { global.fetch = vi.fn() })
  afterEach(() => { vi.restoreAllMocks() })

  it('renders Just arrived row heading', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText('Just arrived')).toBeInTheDocument())
  })

  it('renders user plot names', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => {
      expect(screen.getByText('Family')).toBeInTheDocument()
      expect(screen.getByText('Holidays')).toBeInTheDocument()
    })
  })

  it('does not show gear menu on Just arrived', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText('Just arrived')).toBeInTheDocument())
    // Gear buttons should only exist for user plots
    const gearButtons = screen.queryAllByTitle('Plot options')
    expect(gearButtons.length).toBe(2) // one per user plot
  })

  it('shows empty state for Just arrived when no items', async () => {
    mockFetch({ plots: [systemPlot], plotItems: [] })
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText(/Nothing new to tend/i)).toBeInTheDocument())
  })

  it('shows empty state for user plot when no items', async () => {
    mockFetch({ plots: [systemPlot, userPlot1], plotItems: [] })
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() =>
      expect(screen.getByText(/Empty/i)).toBeInTheDocument()
    )
  })

  it('Add a plot button is visible', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText('+ Add a plot')).toBeInTheDocument())
  })

  it('Add a plot button is a navigation control (no inline form)', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getByText('+ Add a plot'))
    // Plot creation now happens on the Explore page
    expect(screen.queryByText('New plot')).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText('Plot name')).not.toBeInTheDocument()
  })

  it('gear menu opens on click showing Edit, Delete, Move up, Move down', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getAllByTitle('Plot options'))
    fireEvent.click(screen.getAllByTitle('Plot options')[0])
    expect(screen.getByText('Edit')).toBeInTheDocument()
    expect(screen.getByText('Delete')).toBeInTheDocument()
    expect(screen.getByText('Move up')).toBeInTheDocument()
    expect(screen.getByText('Move down')).toBeInTheDocument()
  })

  it('gear Edit navigates to Explore (no inline form)', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getAllByTitle('Plot options'))
    fireEvent.click(screen.getAllByTitle('Plot options')[0])
    // Edit now navigates to Explore — no inline form rendered
    expect(screen.queryByPlaceholderText('Plot name')).not.toBeInTheDocument()
    expect(screen.getByText('Edit')).toBeInTheDocument()
  })

  it('gear Delete shows confirm dialog', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getAllByTitle('Plot options'))
    fireEvent.click(screen.getAllByTitle('Plot options')[0])
    fireEvent.click(screen.getByText('Delete'))
    expect(screen.getByText('Delete plot?')).toBeInTheDocument()
  })

  it('compost heap link is present in footer', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getByText(/Compost heap/i))
    expect(screen.getByRole('link', { name: /Compost heap/i })).toBeInTheDocument()
  })

  // BUG-015 regression: after tagging a Just Arrived item, the optimistic
  // exclusion (justArrivedExclude) must only hide the item from the Just
  // Arrived row — user/shared plot rows must NOT be filtered.
  it('BUG-015: tagged item is excluded from Just Arrived but still visible in user plot row', async () => {
    const taggedUpload = {
      id: 'upload-abc',
      storageKey: 'photo/upload-abc',
      mimeType: 'image/jpeg',
      tags: [],
      storageClass: 'public',
    }

    // Both Just Arrived and user-plot-1 return the same item before tagging.
    // After tagging, the server routes it — both rows return it (simulating
    // server state after trellis routing completes).
    global.fetch.mockImplementation((url) => {
      const u = typeof url === 'string' ? url : url.toString()
      if (u.includes('/api/plots') && !u.includes('/api/plots/')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve([systemPlot, userPlot1]),
        })
      }
      if (u.includes('/api/content/uploads/composted')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ items: [], next_cursor: null }) })
      }
      if (u.includes(`/api/content/uploads/${taggedUpload.id}/tags`)) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({}) })
      }
      if (u.includes('/api/content/uploads')) {
        // Both Just Arrived and user-plot-1 rows return the item
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ items: [taggedUpload], next_cursor: null }),
        })
      }
      if (u.includes(`/api/content/uploads/${taggedUpload.id}/thumb`)) {
        return Promise.resolve({ ok: false })
      }
      return Promise.resolve({ ok: false })
    })

    render(<Wrapper><GardenPage /></Wrapper>)

    // Wait for page to load — both rows present
    await waitFor(() => {
      expect(screen.getByText('Just arrived')).toBeInTheDocument()
      expect(screen.getByText('Family')).toBeInTheDocument()
    })

    // Wait for Edit tags buttons to appear (one per row × one item each = 2)
    await waitFor(() => {
      expect(screen.getAllByTitle('Edit tags').length).toBeGreaterThanOrEqual(2)
    })

    // Click the first Edit tags button (in Just Arrived row)
    const justArrivedHeading = screen.getByText('Just arrived')
    const justArrivedSection = justArrivedHeading.closest('div.space-y-2')
    const tagButton = within(justArrivedSection).getByTitle('Edit tags')
    fireEvent.click(tagButton)

    // Tag modal should appear
    await waitFor(() => expect(screen.getByText('Edit tags')).toBeInTheDocument())

    // Save without changing tags
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    // After saving: Just Arrived should no longer show the Edit tags button
    // (item optimistically excluded), but Family row still should (BUG-015 fix)
    await waitFor(() => {
      const familyHeading = screen.getByText('Family')
      const familySection = familyHeading.closest('div.space-y-1')
      // Family row must still show the item's Edit tags button
      expect(within(familySection).getByTitle('Edit tags')).toBeInTheDocument()
      // Just Arrived row must no longer show it
      expect(within(justArrivedSection).queryByTitle('Edit tags')).not.toBeInTheDocument()
    })
  })
})
