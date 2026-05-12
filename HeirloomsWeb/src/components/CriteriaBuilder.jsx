import { useState } from 'react'

// Serialises the builder's state into a criteria expression tree.
export function builderStateToCriteria({ tags, mediaType, fromDate, toDate, hasLocation, isReceived }) {
  const operands = []
  tags.forEach((tag) => operands.push({ type: 'tag', tag }))
  if (mediaType) operands.push({ type: 'media_type', value: mediaType })
  if (fromDate) operands.push({ type: 'taken_after', date: fromDate })
  if (toDate) operands.push({ type: 'taken_before', date: toDate })
  if (hasLocation) operands.push({ type: 'has_location' })
  if (isReceived) operands.push({ type: 'is_received' })
  if (operands.length === 0) return null
  if (operands.length === 1) return operands[0]
  return { type: 'and', operands }
}

// Deserialises a criteria expression tree back into builder state for editing.
// Only handles the flat AND-of-atoms shape that the builder produces.
export function criteriaToBuilderState(criteria) {
  const empty = { tags: [], mediaType: null, fromDate: '', toDate: '', hasLocation: false, isReceived: false }
  if (!criteria) return empty

  const atoms = criteria.type === 'and' ? criteria.operands : [criteria]
  const state = { ...empty }
  for (const atom of atoms) {
    if (atom.type === 'tag') state.tags = [...state.tags, atom.tag]
    else if (atom.type === 'media_type') state.mediaType = atom.value
    else if (atom.type === 'taken_after') state.fromDate = atom.date
    else if (atom.type === 'taken_before') state.toDate = atom.date
    else if (atom.type === 'has_location') state.hasLocation = true
    else if (atom.type === 'is_received') state.isReceived = true
  }
  return state
}

// Compact criteria description for display in banners / edit mode hints.
export function criteriaDescription(criteria) {
  if (!criteria) return 'no criteria'
  const atoms = criteria.type === 'and' ? criteria.operands : [criteria]
  const parts = []
  const tags = atoms.filter((a) => a.type === 'tag').map((a) => a.tag)
  if (tags.length) parts.push(`tags: ${tags.join(', ')}`)
  const mt = atoms.find((a) => a.type === 'media_type')
  if (mt) parts.push(mt.value === 'image' ? 'photos only' : 'videos only')
  if (atoms.find((a) => a.type === 'taken_after')) parts.push('date from set')
  if (atoms.find((a) => a.type === 'taken_before')) parts.push('date to set')
  if (atoms.find((a) => a.type === 'has_location')) parts.push('has location')
  if (atoms.find((a) => a.type === 'is_received')) parts.push('received only')
  return parts.length ? parts.join(' · ') : 'no criteria'
}

// Tag input with add/remove chip UI.
function TagInput({ tags, onChange }) {
  const [input, setInput] = useState('')

  function addTag() {
    const t = input.trim().toLowerCase()
    if (t && !tags.includes(t)) onChange([...tags, t])
    setInput('')
  }

  return (
    <div>
      <div className="flex gap-1 flex-wrap mb-1">
        {tags.map((t) => (
          <span key={t} className="inline-flex items-center gap-1 px-2 py-0.5 bg-forest-08 border border-forest-25 rounded-full text-xs font-sans text-forest">
            {t}
            <button onClick={() => onChange(tags.filter((x) => x !== t))}
              className="text-forest-50 hover:text-earth transition-colors leading-none">&times;</button>
          </span>
        ))}
      </div>
      <div className="flex gap-1">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag() } }}
          placeholder="Add tag…"
          className="flex-1 px-2 py-1 border border-forest-25 rounded text-sm font-sans bg-parchment text-forest placeholder:text-text-muted focus:outline-none focus:border-forest"
        />
        <button onClick={addTag}
          className="px-2 py-1 text-sm bg-forest text-parchment rounded hover:opacity-90 transition-opacity">
          +
        </button>
      </div>
    </div>
  )
}

// Criteria builder component. Controlled: reads state from props, notifies on change.
export function CriteriaBuilder({ state, onChange }) {
  const { tags, mediaType, fromDate, toDate, hasLocation, isReceived } = state

  function set(patch) { onChange({ ...state, ...patch }) }

  return (
    <div className="space-y-3 text-sm font-sans">
      <div>
        <label className="block text-text-muted mb-1">Tags (all must match)</label>
        <TagInput tags={tags} onChange={(t) => set({ tags: t })} />
      </div>

      <div>
        <label className="block text-text-muted mb-1">Media type</label>
        <div className="flex gap-1">
          {[['', 'All'], ['image', 'Photos'], ['video', 'Videos']].map(([val, label]) => (
            <button key={val}
              onClick={() => set({ mediaType: val || null })}
              className={`px-3 py-1 rounded-button text-xs border transition-colors ${
                mediaType === (val || null)
                  ? 'bg-forest text-parchment border-forest'
                  : 'bg-parchment text-forest border-forest-25 hover:border-forest'
              }`}>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex gap-3">
        <div className="flex-1">
          <label className="block text-text-muted mb-1">Date taken — from</label>
          <input type="date" value={fromDate}
            onChange={(e) => set({ fromDate: e.target.value })}
            className="w-full px-2 py-1 border border-forest-25 rounded text-sm bg-parchment text-forest focus:outline-none focus:border-forest"
          />
        </div>
        <div className="flex-1">
          <label className="block text-text-muted mb-1">Date taken — to</label>
          <input type="date" value={toDate}
            onChange={(e) => set({ toDate: e.target.value })}
            className="w-full px-2 py-1 border border-forest-25 rounded text-sm bg-parchment text-forest focus:outline-none focus:border-forest"
          />
        </div>
      </div>

      <div className="flex gap-4">
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input type="checkbox" checked={hasLocation}
            onChange={(e) => set({ hasLocation: e.target.checked })}
            className="accent-forest" />
          <span className="text-forest">Has location</span>
        </label>
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input type="checkbox" checked={isReceived}
            onChange={(e) => set({ isReceived: e.target.checked })}
            className="accent-forest" />
          <span className="text-forest">Received items only</span>
        </label>
      </div>
    </div>
  )
}

export const emptyCriteriaState = () => ({
  tags: [],
  mediaType: null,
  fromDate: '',
  toDate: '',
  hasLocation: false,
  isReceived: false,
})
