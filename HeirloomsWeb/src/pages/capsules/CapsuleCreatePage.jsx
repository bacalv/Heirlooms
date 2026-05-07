import { useEffect, useState, useRef } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../AuthContext'
import { apiFetch, buildUnlockAt, API_URL } from '../../api'
import { WaxSealOlive } from '../../brand/WaxSealOlive'
import { WorkingDots } from '../../brand/WorkingDots'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { DatePickerDropdowns } from '../../components/DatePickerDropdowns'
import { PhotoPickerModal } from '../../components/PhotoPickerModal'

const inputClass =
  'w-full px-3 py-2 border border-forest-15 rounded-button text-sm bg-parchment text-forest focus:outline-none focus:ring-2 focus:ring-forest-25'

function IncludeStrip({ uploadIds, apiKey, onEdit }) {
  const [thumbs, setThumbs] = useState({})

  useEffect(() => {
    uploadIds.forEach((id) => {
      if (thumbs[id]) return
      const url = `${API_URL}/api/content/uploads/${id}/thumb`
      fetch(url, { headers: { 'X-Api-Key': apiKey } })
        .then((r) => r.ok ? r.blob() : Promise.reject())
        .then((blob) => setThumbs((prev) => ({ ...prev, [id]: URL.createObjectURL(blob) })))
        .catch(() => {})
    })
  }, [uploadIds, apiKey, thumbs])

  if (uploadIds.length === 0) {
    return (
      <button type="button" onClick={onEdit}
        className="px-4 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity">
        Choose what to include
      </button>
    )
  }

  return (
    <div className="border border-forest-15 rounded-card p-3 space-y-2">
      <div className="flex gap-2 overflow-x-auto pb-1">
        {uploadIds.map((id) => (
          <div key={id} className="flex-shrink-0 w-16 h-16 rounded border border-forest-08 overflow-hidden bg-forest-04">
            {thumbs[id] && <img src={thumbs[id]} alt="" className="w-full h-full object-cover" />}
          </div>
        ))}
      </div>
      <div className="flex items-center justify-between">
        <span className="text-xs text-text-muted">
          {uploadIds.length} {uploadIds.length === 1 ? 'item' : 'items'} selected
        </span>
        <button type="button" onClick={onEdit} className="text-xs text-text-muted hover:text-forest transition-colors">
          Choose what to include
        </button>
      </div>
    </div>
  )
}

export function CapsuleCreatePage() {
  const { apiKey } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [recipients, setRecipients] = useState([''])
  const [date, setDate] = useState({ day: '', month: '', year: '' })
  const [uploadIds, setUploadIds] = useState([])
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState(null)
  const [validationErrors, setValidationErrors] = useState({})
  const [showPicker, setShowPicker] = useState(false)
  const [showDiscardDialog, setShowDiscardDialog] = useState(false)

  useEffect(() => {
    document.title = 'Start a capsule · Heirlooms'

    const include = searchParams.get('include')
    if (include) {
      const ids = include.split(',').filter(Boolean)
      setUploadIds(ids)
    }
  }, [searchParams])

  const hasData = recipients.some((r) => r.trim()) || date.day || uploadIds.length || message.trim()

  const firstRecipient = recipients.find((r) => r.trim())
  const messagePlaceholder = firstRecipient
    ? `Write something for ${firstRecipient}…`
    : recipients.filter((r) => r.trim()).length > 1
    ? 'Write something for them…'
    : 'Write a message…'

  function validate(shape) {
    const errs = {}
    if (!recipients.some((r) => r.trim())) errs.recipients = '"For" needs at least one recipient.'
    if (!date.day || !date.month || !date.year) errs.date = '"To open on" must be a full date.'
    if (shape === 'sealed' && uploadIds.length === 0) errs.uploads = 'A sealed capsule needs at least one thing inside.'
    return errs
  }

  async function handleSubmit(shape) {
    const errs = validate(shape)
    if (Object.keys(errs).length) { setValidationErrors(errs); return }
    setValidationErrors({})
    setSubmitError(null)
    setSubmitting(true)
    try {
      const r = await apiFetch('/api/capsules', apiKey, {
        method: 'POST',
        body: JSON.stringify({
          shape,
          unlock_at: buildUnlockAt(date.day, date.month, date.year),
          recipients: recipients.filter((r) => r.trim()),
          upload_ids: uploadIds,
          message,
        }),
      })
      if (!r.ok) throw new Error()
      const data = await r.json()
      if (shape === 'sealed') {
        navigate(`/capsules/${data.id}?sealed=1`)
      } else {
        navigate(`/capsules/${data.id}`)
      }
    } catch {
      setSubmitError(true)
    } finally {
      setSubmitting(false)
    }
  }

  function handleCancel() {
    if (hasData) setShowDiscardDialog(true)
    else navigate('/capsules')
  }

  return (
    <main className="max-w-2xl mx-auto px-4 py-8">
      <button onClick={handleCancel} className="text-sm text-text-muted hover:text-forest transition-colors mb-8 block">
        ← Capsules
      </button>

      <p className="font-serif italic text-forest text-xl mb-8">Plant something for someone.</p>

      <div className="space-y-6">
        {/* For */}
        <div>
          <label className="block text-sm font-sans text-forest mb-1">For</label>
          {recipients.map((r, i) => (
            <div key={i} className="flex items-center gap-2 mb-2">
              <input
                value={r}
                onChange={(e) => {
                  const next = [...recipients]
                  next[i] = e.target.value
                  setRecipients(next)
                }}
                placeholder="Sophie"
                className={inputClass}
              />
              {recipients.length > 1 && (
                <button type="button" onClick={() => setRecipients((p) => p.filter((_, j) => j !== i))}
                  className="text-text-muted hover:text-earth text-xl leading-none">×</button>
              )}
            </div>
          ))}
          <button type="button" onClick={() => setRecipients((p) => [...p, ''])}
            className="text-sm text-text-muted hover:text-forest transition-colors">+ add another</button>
          {validationErrors.recipients && (
            <p className="text-xs font-serif italic text-earth mt-1">{validationErrors.recipients}</p>
          )}
        </div>

        {/* To open on */}
        <div>
          <label className="block text-sm font-sans text-forest mb-1">To open on</label>
          <DatePickerDropdowns
            day={date.day} month={date.month} year={date.year}
            onChange={setDate}
          />
          {validationErrors.date && (
            <p className="text-xs font-serif italic text-earth mt-1">{validationErrors.date}</p>
          )}
        </div>

        {/* Include */}
        <div>
          <label className="block text-sm font-sans text-forest mb-1">Include</label>
          <IncludeStrip
            uploadIds={uploadIds}
            apiKey={apiKey}
            onEdit={() => setShowPicker(true)}
          />
          {validationErrors.uploads && (
            <p className="text-xs font-serif italic text-earth mt-1">{validationErrors.uploads}</p>
          )}
        </div>

        {/* Message */}
        <div>
          <label className="block text-sm font-sans text-forest mb-1">Message</label>
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder={messagePlaceholder}
            rows={6}
            className={`${inputClass} resize-none`}
          />
        </div>
      </div>

      {submitError && (
        <div className="mt-6 p-4 rounded-card border border-earth/30 bg-earth-10 text-center space-y-2">
          <p className="font-serif italic text-earth">didn't take</p>
          <p className="text-sm text-text-body">Your capsule wasn't started.</p>
          <button onClick={() => setSubmitError(null)}
            className="text-sm text-forest underline">Try again</button>
        </div>
      )}

      <div className="flex items-center justify-between mt-8 flex-wrap gap-4">
        <button type="button" onClick={handleCancel} disabled={submitting}
          className="px-4 py-2 rounded-button text-sm border border-forest-25 text-forest hover:border-forest transition-colors disabled:opacity-40">
          Cancel
        </button>
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => handleSubmit('sealed')} disabled={submitting}
            className="flex items-center gap-2 px-4 py-2 rounded-button text-sm border border-forest text-forest hover:bg-forest-04 transition-colors disabled:opacity-40">
            {submitting ? <WorkingDots size="sm" /> : <><WaxSealOlive size={14} className="text-bloom" />Seal capsule</>}
          </button>
          <button type="button" onClick={() => handleSubmit('open')} disabled={submitting}
            className="px-5 py-2 rounded-button text-sm bg-forest text-parchment hover:opacity-90 transition-opacity disabled:opacity-40">
            {submitting ? <WorkingDots size="sm" /> : 'Start capsule'}
          </button>
        </div>
      </div>

      {showPicker && (
        <PhotoPickerModal
          initialSelectedIds={uploadIds}
          onDone={(ids) => { setUploadIds(ids); setShowPicker(false) }}
          onCancel={() => setShowPicker(false)}
        />
      )}

      {showDiscardDialog && (
        <ConfirmDialog
          title="Discard this capsule?"
          titleItalic={false}
          body="Your capsule hasn't been started."
          primaryLabel="Discard"
          primaryClass="bg-earth text-parchment hover:opacity-90"
          cancelLabel="Keep editing"
          onConfirm={() => navigate('/capsules')}
          onCancel={() => setShowDiscardDialog(false)}
          focusPrimary={false}
        />
      )}
    </main>
  )
}
