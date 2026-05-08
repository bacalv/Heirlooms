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
      expect(screen.getByText(/No items match this plot's criteria/i)).toBeInTheDocument()
    )
  })

  it('Add a plot button is visible', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => expect(screen.getByText('+ Add a plot')).toBeInTheDocument())
  })

  it('clicking Add a plot shows the inline form', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getByText('+ Add a plot'))
    fireEvent.click(screen.getByText('+ Add a plot'))
    expect(screen.getByText('New plot')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Plot name')).toBeInTheDocument()
  })

  it('form Cancel hides the inline form', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getByText('+ Add a plot'))
    fireEvent.click(screen.getByText('+ Add a plot'))
    fireEvent.click(screen.getByText('Cancel'))
    expect(screen.queryByText('New plot')).not.toBeInTheDocument()
    expect(screen.getByText('+ Add a plot')).toBeInTheDocument()
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

  it('gear Edit pre-fills the form with plot name', async () => {
    mockFetch()
    render(<Wrapper><GardenPage /></Wrapper>)
    await waitFor(() => screen.getAllByTitle('Plot options'))
    fireEvent.click(screen.getAllByTitle('Plot options')[0])
    fireEvent.click(screen.getByText('Edit'))
    const input = screen.getByPlaceholderText('Plot name')
    expect(input.value).toBe('Family')
    expect(screen.getByText('Edit plot')).toBeInTheDocument()
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
})
