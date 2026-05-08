import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../AuthContext'
import { apiFetch } from '../api'
import { PhotoGrid } from '../components/PhotoGrid'
import { WorkingDots } from '../brand/WorkingDots'

export function ExplorePage() {
  const { apiKey } = useAuth()
  const [items, setItems] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState(null)

  const fetchPage = useCallback(async (cursor) => {
    const url = cursor
      ? `/api/content/uploads?limit=50&cursor=${encodeURIComponent(cursor)}`
      : '/api/content/uploads?limit=50'
    const r = await apiFetch(url, apiKey)
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return r.json()
  }, [apiKey])

  useEffect(() => {
    document.title = 'Explore · Heirlooms'
    fetchPage(null)
      .then((data) => {
        setItems(data.items ?? [])
        setNextCursor(data.next_cursor ?? null)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [fetchPage])

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) return
    setLoadingMore(true)
    try {
      const data = await fetchPage(nextCursor)
      setItems((prev) => [...prev, ...(data.items ?? [])])
      setNextCursor(data.next_cursor ?? null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingMore(false)
    }
  }

  if (loading) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-8">
        <div className="flex justify-center py-20">
          <WorkingDots size="lg" label="Loading…" />
        </div>
      </main>
    )
  }

  if (error) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-8">
        <p className="text-center text-earth font-serif italic py-20">
          Something went wrong — {error}
        </p>
      </main>
    )
  }

  return (
    <main className="max-w-7xl mx-auto px-4 py-8">
      <h1 className="font-serif italic text-forest text-xl mb-6">Explore</h1>

      {items.length === 0 ? (
        <p className="text-text-muted text-sm font-sans text-center py-16">Nothing here yet.</p>
      ) : (
        <>
          <PhotoGrid
            uploads={items}
            getPhotoHref={(id) => `/photos/${id}`}
            cols="5"
          />
          {nextCursor && (
            <div className="mt-8 flex justify-center">
              <button
                onClick={handleLoadMore}
                disabled={loadingMore}
                className="px-4 py-2 text-sm font-sans text-forest border border-forest-25 rounded-button hover:bg-forest-04 transition-colors disabled:opacity-40"
              >
                {loadingMore ? <WorkingDots size="sm" /> : 'Load more'}
              </button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
