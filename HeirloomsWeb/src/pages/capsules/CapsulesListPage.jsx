import { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../AuthContext'
import { apiFetch, formatUnlockDate, capsuleTitle } from '../../api'
import { WaxSealOlive } from '../../brand/WaxSealOlive'

const FILTERS = [
  { label: 'Active', value: 'open,sealed' },
  { label: 'Delivered', value: 'delivered' },
  { label: 'Cancelled', value: 'cancelled' },
  { label: 'All', value: 'open,sealed,delivered,cancelled' },
]

const SORTS = [
  { label: 'Soonest first', value: 'unlock_at', dir: 'asc' },
  { label: 'Most recent edits', value: 'updated_at', dir: 'desc' },
  { label: 'Newest first', value: 'created_at', dir: 'desc' },
]

function CapsuleCard({ capsule }) {
  const isSealed = capsule.state === 'sealed'
  const isDelivered = capsule.state === 'delivered'
  const isCancelled = capsule.state === 'cancelled'

  const cardBg = isDelivered
    ? 'bg-bloom-15'
    : isCancelled
    ? 'bg-earth-10'
    : 'bg-parchment'

  const count = capsule.upload_count ?? 0

  return (
    <Link
      to={`/capsules/${capsule.id}`}
      className={`block rounded-card border border-forest-15 p-5 hover:shadow-md transition-shadow relative ${cardBg}`}
    >
      {isSealed && (
        <div className="absolute top-3 left-3">
          <WaxSealOlive size={18} />
        </div>
      )}
      <div className={isSealed ? 'mt-6' : ''}>
        <p className="font-serif italic text-forest text-lg leading-snug">
          {capsuleTitle(capsule.recipients)}
        </p>
        <p className="text-sm text-forest mt-1">
          To open on {formatUnlockDate(capsule.unlock_at)}
        </p>
      </div>
      <p className="text-xs text-text-muted mt-4 text-right">
        {count} {count === 1 ? 'item' : 'items'}
      </p>
    </Link>
  )
}

function SkeletonCard() {
  return (
    <div className="rounded-card border border-forest-08 p-5 bg-parchment">
      <div className="h-5 bg-forest-08 rounded w-3/4 mb-2" />
      <div className="h-4 bg-forest-08 rounded w-1/2 mb-6" />
      <div className="h-3 bg-forest-08 rounded w-12 ml-auto" />
    </div>
  )
}

const selectClass =
  'px-3 py-1.5 border border-forest-15 rounded-button text-sm bg-parchment text-forest focus:outline-none focus:ring-2 focus:ring-forest-25'

export function CapsulesListPage() {
  const { apiKey } = useAuth()
  const [capsules, setCapsules] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [filter, setFilter] = useState('open,sealed')
  const [sort, setSort] = useState('unlock_at')

  useEffect(() => { document.title = 'Capsules · Heirlooms' }, [])

  const fetchCapsules = useCallback(async () => {
    setError(null)
    const apiOrder = sort === 'created_at' ? 'updated_at' : sort
    try {
      const r = await apiFetch(`/api/capsules?state=${filter}&order=${apiOrder}`, apiKey)
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const data = await r.json()
      let list = data.capsules ?? []

      if (sort === 'created_at') {
        list = [...list].sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
      } else if (sort === 'unlock_at') {
        list = [...list].sort((a, b) => new Date(a.unlock_at) - new Date(b.unlock_at))
      } else {
        list = [...list].sort((a, b) => new Date(b.updated_at) - new Date(a.updated_at))
      }

      setCapsules(list)
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }, [apiKey, filter, sort])

  useEffect(() => { setLoading(true); fetchCapsules() }, [fetchCapsules])

  const sortLabel = SORTS.find((s) => s.value === sort)?.label ?? 'Soonest first'

  const hasAnyCapsules = capsules.length > 0 || !loading

  const emptyAllTime = !loading && !error && capsules.length === 0 && filter === 'open,sealed'

  return (
    <main className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex items-start justify-between gap-4 mb-8 flex-wrap">
        <div className="flex items-center gap-4">
          <h1 className="text-2xl font-sans font-medium text-forest">Capsules</h1>
          <Link
            to="/capsules/new"
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity"
          >
            Start a capsule
          </Link>
        </div>

        {!error && !(emptyAllTime) && (
          <div className="flex items-center gap-2">
            <select
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className={selectClass}
              aria-label="Filter capsules"
            >
              {FILTERS.map((f) => (
                <option key={f.value} value={f.value}>{f.label}</option>
              ))}
            </select>
            <select
              value={sort}
              onChange={(e) => setSort(e.target.value)}
              className={selectClass}
              aria-label="Sort capsules"
            >
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </div>
        )}
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      )}

      {error && (
        <div className="text-center py-20 space-y-3">
          <p className="font-serif italic text-earth text-lg">didn't take</p>
          <p className="text-sm text-text-body">The capsules didn't load.</p>
          <button onClick={fetchCapsules}
            className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity">
            Try again
          </button>
        </div>
      )}

      {!loading && !error && capsules.length === 0 && emptyAllTime && (
        <div className="text-center py-24 space-y-4">
          <p className="font-serif italic text-forest text-xl leading-relaxed">
            A garden grows things to keep.<br />A capsule grows things to give.
          </p>
          <Link to="/capsules/new"
            className="inline-block px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity">
            Start a capsule
          </Link>
        </div>
      )}

      {!loading && !error && capsules.length === 0 && !emptyAllTime && (
        <p className="text-sm text-text-muted mt-4">No capsules in this filter.</p>
      )}

      {!loading && !error && capsules.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {capsules.map((cap) => (
            <CapsuleCard key={cap.id} capsule={cap} />
          ))}
        </div>
      )}
    </main>
  )
}
