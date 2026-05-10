import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { OliveBranchIcon } from '../brand/OliveBranchIcon'
import { API_URL } from '../api'

export function LoginPage({ onLogin }) {
  const [value, setValue] = useState('')
  const [error, setError] = useState(null)
  const [checking, setChecking] = useState(false)
  const inputRef = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()
  const from = location.state?.from ?? '/'

  useEffect(() => { inputRef.current?.focus() }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    const key = value.trim()
    if (!key) return
    setChecking(true)
    setError(null)
    try {
      const r = await fetch(`${API_URL}/api/content/uploads`, { headers: { 'X-Api-Key': key } })
      if (r.status === 401) setError('Incorrect API key.')
      else if (!r.ok) setError(`Server error (${r.status}).`)
      else { onLogin(key); navigate(from, { replace: true }) }
    } catch {
      setError('Could not reach the server.')
    } finally {
      setChecking(false)
    }
  }

  return (
    <div className="min-h-screen bg-parchment flex items-center justify-center">
      <div className="bg-white rounded-card shadow-sm border border-forest-08 p-8 w-full max-w-sm">
        <div className="flex items-center gap-2 mb-1">
          <OliveBranchIcon width={20} />
          <span className="font-serif italic text-[17px] text-forest">Heirlooms</span>
        </div>
        <p className="text-sm text-text-muted mb-6">Enter your API key to continue.</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            ref={inputRef}
            type="password"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="API key"
            className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25"
          />
          {error && <p className="text-sm text-earth font-serif italic">{error}</p>}
          <button
            type="submit"
            disabled={checking || !value.trim()}
            className="w-full bg-forest text-parchment py-2 rounded-button text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity"
          >
            {checking ? 'Checking…' : 'Sign in'}
          </button>
        </form>
        <p className="text-[10px] text-text-muted/40 text-right mt-4">{import.meta.env.VITE_COMMIT ?? 'dev'}</p>
      </div>
    </div>
  )
}
