import { useEffect, useState } from 'react'
import { useAuth } from '../AuthContext'
import { apiFetch } from '../api'
import { WorkingDots } from '../brand/WorkingDots'
import { BrandModal } from '../components/BrandModal'
import { CriteriaBuilder, builderStateToCriteria, criteriaToBuilderState, criteriaDescription, emptyCriteriaState } from '../components/CriteriaBuilder'
import { StagingPanel } from '../components/StagingPanel'

// ---- Flow form (create / edit) -------------------------------------------

function FlowForm({ plots, initial, onSave, onCancel, saving, error }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [targetPlotId, setTargetPlotId] = useState(initial?.targetPlotId ?? '')
  const [requiresStaging, setRequiresStaging] = useState(initial?.requiresStaging ?? true)
  const [criteriaState, setCriteriaState] = useState(
    initial?.criteria ? criteriaToBuilderState(initial.criteria) : emptyCriteriaState()
  )

  const collectionPlots = plots.filter((p) => !p.criteria && !p.is_system_defined)
  const selectedPlot = collectionPlots.find((p) => p.id === targetPlotId) ?? null

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim() || !targetPlotId) return
    onSave({
      name: name.trim(),
      criteria: builderStateToCriteria(criteriaState),
      targetPlotId,
      requiresStaging,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3 text-sm font-sans">
      <div>
        <label className="block text-text-muted mb-1">Flow name</label>
        <input type="text" value={name} onChange={(e) => setName(e.target.value)} required
          placeholder="e.g. Photos of Sadaar → Family"
          className="w-full px-3 py-1.5 border border-forest-25 rounded text-forest bg-parchment focus:outline-none focus:border-forest"
        />
      </div>

      {!initial && (
        <div>
          <label className="block text-text-muted mb-1">Target collection plot</label>
          {collectionPlots.length === 0 ? (
            <p className="text-text-muted text-xs">No collection plots yet. Create one from the Garden (no criteria).</p>
          ) : (
            <select value={targetPlotId} onChange={(e) => setTargetPlotId(e.target.value)} required
              className="w-full px-3 py-1.5 border border-forest-25 rounded text-forest bg-parchment focus:outline-none focus:border-forest">
              <option value="">Select a plot…</option>
              {collectionPlots.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          )}
        </div>
      )}

      <div>
        <label className="block text-text-muted mb-2">Criteria (items matching these enter the flow)</label>
        <CriteriaBuilder state={criteriaState} onChange={setCriteriaState} />
      </div>

      {selectedPlot?.visibility === 'shared' && (
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input type="checkbox" checked={requiresStaging} onChange={(e) => setRequiresStaging(e.target.checked)}
            className="accent-forest" />
          <span className="text-forest">Require staging review before items enter the plot</span>
        </label>
      )}

      {error && <p className="text-earth text-xs">{error}</p>}

      <div className="flex gap-2 justify-end">
        <button type="button" onClick={onCancel}
          className="px-3 py-1.5 text-text-muted hover:text-forest transition-colors">
          Cancel
        </button>
        <button type="submit" disabled={saving || !name.trim() || (!initial && !targetPlotId)}
          className="px-4 py-1.5 bg-forest text-parchment rounded-button hover:opacity-90 transition-opacity disabled:opacity-40">
          {saving ? '…' : initial ? 'Update flow' : 'Create flow'}
        </button>
      </div>
    </form>
  )
}

// ---- Flow card -----------------------------------------------------------

function FlowCard({ flow, plotName, isSharedPlot, onEdit, onDelete }) {
  const [showStaging, setShowStaging] = useState(false)
  const [pendingCount, setPendingCount] = useState(null)
  const { apiKey } = useAuth()

  useEffect(() => {
    if (!flow.requiresStaging) return
    apiFetch(`/api/flows/${flow.id}/staging`, apiKey)
      .then((r) => r.ok ? r.json() : [])
      .then((items) => setPendingCount(Array.isArray(items) ? items.length : 0))
      .catch(() => {})
  }, [flow.id, flow.requiresStaging, apiKey])

  return (
    <div className="border border-forest-08 rounded-card bg-parchment p-4">
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <p className="font-sans font-medium text-forest text-sm truncate">{flow.name}</p>
          <p className="text-xs text-text-muted mt-0.5">
            → <span className="text-forest">{plotName ?? flow.targetPlotId}</span>
          </p>
          <p className="text-xs text-text-muted mt-0.5">{criteriaDescription(flow.criteria)}</p>
          <p className="text-xs text-text-muted mt-0.5">
            {flow.requiresStaging ? 'Staging required' : 'Auto-add to collection'}
          </p>
        </div>
        <div className="flex gap-1 flex-shrink-0">
          {flow.requiresStaging && (
            <button onClick={() => setShowStaging((s) => !s)}
              className="px-2 py-1 text-xs border border-forest-15 rounded-button text-forest hover:bg-forest-08 transition-colors">
              {showStaging ? 'Hide staging' : pendingCount ? `Review (${pendingCount})` : 'Review'}
            </button>
          )}
          <button onClick={() => onEdit(flow)}
            className="px-2 py-1 text-xs border border-forest-15 rounded-button text-text-muted hover:text-forest transition-colors">
            Edit
          </button>
          <button onClick={() => onDelete(flow.id)}
            className="px-2 py-1 text-xs text-text-muted hover:text-earth transition-colors">
            Delete
          </button>
        </div>
      </div>

      {showStaging && (
        <div className="mt-4 border-t border-forest-08 pt-4">
          <StagingPanel plotId={flow.targetPlotId} flowId={flow.id} apiKey={apiKey} isSharedPlot={isSharedPlot} />
        </div>
      )}
    </div>
  )
}

// ---- Main page -----------------------------------------------------------

export function FlowsPage() {
  const { apiKey } = useAuth()
  const [flows, setFlows] = useState([])
  const [plots, setPlots] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [editFlow, setEditFlow] = useState(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [deleteConfirm, setDeleteConfirm] = useState(null)

  function reload() {
    Promise.all([
      apiFetch('/api/flows', apiKey).then((r) => r.ok ? r.json() : []),
      apiFetch('/api/plots', apiKey).then((r) => r.ok ? r.json() : []),
    ])
      .then(([f, p]) => {
        setFlows(Array.isArray(f) ? f : [])
        setPlots(Array.isArray(p) ? p : [])
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    document.title = 'Flows · Heirlooms'
    reload()
  }, [apiKey])

  async function handleCreate({ name, criteria, targetPlotId, requiresStaging }) {
    setSaving(true); setSaveError(null)
    try {
      const r = await apiFetch('/api/flows', apiKey, {
        method: 'POST',
        body: JSON.stringify({ name, criteria, targetPlotId, requiresStaging }),
      })
      if (!r.ok) {
        const msg = await r.text()
        throw new Error(msg || `HTTP ${r.status}`)
      }
      setShowCreate(false)
      reload()
    } catch (e) { setSaveError(e.message) } finally { setSaving(false) }
  }

  async function handleUpdate({ name, criteria, requiresStaging }) {
    if (!editFlow) return
    setSaving(true); setSaveError(null)
    try {
      const r = await apiFetch(`/api/flows/${editFlow.id}`, apiKey, {
        method: 'PUT',
        body: JSON.stringify({ name, criteria, requiresStaging }),
      })
      if (!r.ok) {
        const msg = await r.text()
        throw new Error(msg || `HTTP ${r.status}`)
      }
      setEditFlow(null)
      reload()
    } catch (e) { setSaveError(e.message) } finally { setSaving(false) }
  }

  async function handleDelete(id) {
    try {
      await apiFetch(`/api/flows/${id}`, apiKey, { method: 'DELETE' })
      setDeleteConfirm(null)
      reload()
    } catch (_) {}
  }

  const plotName = (id) => plots.find((p) => p.id === id)?.name

  if (loading) return (
    <main className="max-w-3xl mx-auto px-4 py-8 flex justify-center">
      <WorkingDots size="lg" label="Loading…" />
    </main>
  )

  return (
    <main className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="font-serif italic text-forest text-xl">Flows</h1>
        <button onClick={() => { setShowCreate(true); setSaveError(null) }}
          className="px-3 py-1.5 text-sm bg-forest text-parchment rounded-button hover:opacity-90 transition-opacity">
          New flow
        </button>
      </div>

      {showCreate && (
        <BrandModal onClose={() => setShowCreate(false)} width="max-w-lg">
          <div className="p-6">
            <h2 className="font-serif italic text-forest text-lg mb-4 mt-0">New flow</h2>
            <FlowForm plots={plots} onSave={handleCreate} onCancel={() => setShowCreate(false)}
              saving={saving} error={saveError} />
          </div>
        </BrandModal>
      )}

      {editFlow && (
        <BrandModal onClose={() => setEditFlow(null)} width="max-w-lg">
          <div className="p-6">
            <h2 className="font-serif italic text-forest text-lg mb-4 mt-0">Edit "{editFlow.name}"</h2>
            <FlowForm plots={plots} initial={editFlow} onSave={handleUpdate}
              onCancel={() => setEditFlow(null)} saving={saving} error={saveError} />
          </div>
        </BrandModal>
      )}

      {deleteConfirm && (
        <BrandModal onClose={() => setDeleteConfirm(null)} width="max-w-sm">
          <div className="p-6 space-y-4">
            <p className="font-sans text-sm text-forest">
              Delete "<span className="font-medium">{deleteConfirm.name}</span>"? Items already approved into the collection will be kept.
            </p>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setDeleteConfirm(null)}
                className="px-3 py-1.5 text-text-muted hover:text-forest text-sm transition-colors">
                Cancel
              </button>
              <button onClick={() => handleDelete(deleteConfirm.id)}
                className="px-3 py-1.5 bg-earth text-parchment rounded-button text-sm hover:opacity-90 transition-opacity">
                Delete
              </button>
            </div>
          </div>
        </BrandModal>
      )}

      {flows.length === 0 ? (
        <p className="text-text-muted text-sm font-sans text-center py-16">
          No flows yet. Create one to automatically route items into a collection plot.
        </p>
      ) : (
        <div className="space-y-3">
          {flows.map((f) => (
            <FlowCard key={f.id} flow={f} plotName={plotName(f.targetPlotId)}
              isSharedPlot={plots.find((p) => p.id === f.targetPlotId)?.visibility === 'shared'}
              onEdit={(flow) => { setEditFlow(flow); setSaveError(null) }}
              onDelete={(id) => setDeleteConfirm(flows.find((x) => x.id === id))}
            />
          ))}
        </div>
      )}
    </main>
  )
}
