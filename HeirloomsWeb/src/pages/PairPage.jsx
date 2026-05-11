import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { WorkingDots } from '../brand/WorkingDots'
import { pairingQr, pairingStatus } from '../api'
import { unwrapMasterKeyForDevice, toB64url, ALG_P256_ECDH_HKDF_V1 } from '../crypto/vaultCrypto'
import { unlock } from '../crypto/vaultSession'
import { savePairingMaterial } from '../crypto/webPairingStore'

export function PairPage({ onPaired }) {
  const [code, setCode] = useState('')
  const [phase, setPhase] = useState('enter') // enter | qr | done | error
  const [errorMsg, setErrorMsg] = useState('')
  const [qrDataUrl, setQrDataUrl] = useState(null)
  const privateKeyRef = useRef(null)
  const pollRef = useRef(null)

  useEffect(() => {
    return () => { if (pollRef.current) clearInterval(pollRef.current) }
  }, [])

  async function handleSubmit(e) {
    e.preventDefault()
    const c = code.trim()
    if (!c) return
    setErrorMsg('')
    try {
      const r = await pairingQr(c)
      if (!r.ok) { setErrorMsg('Invalid or expired code. Please try again.'); return }
      const { session_id } = await r.json()

      const keypair = await crypto.subtle.generateKey(
        { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
      )
      privateKeyRef.current = keypair.privateKey
      const spkiBytes = new Uint8Array(await crypto.subtle.exportKey('spki', keypair.publicKey))
      const pubkeyB64url = toB64url(spkiBytes)

      const qrPayload = JSON.stringify({ session_id, pubkey: pubkeyB64url })
      const dataUrl = await QRCode.toDataURL(qrPayload, { errorCorrectionLevel: 'M', width: 300 })
      setQrDataUrl(dataUrl)
      setPhase('qr')

      pollRef.current = setInterval(async () => {
        try {
          const pr = await pairingStatus(session_id)
          if (pr.status === 404) {
            clearInterval(pollRef.current)
            setErrorMsg('Code expired — go back and try again.')
            setPhase('error')
            return
          }
          if (!pr.ok) return
          const data = await pr.json()
          if (data.state === 'complete') {
            clearInterval(pollRef.current)
            const wrappedBytes = Uint8Array.from(atob(data.wrapped_master_key.replace(/-/g,'+').replace(/_/g,'/')), c => c.charCodeAt(0))
            const masterKey = await unwrapMasterKeyForDevice(wrappedBytes, privateKeyRef.current)
            await savePairingMaterial({
              privateKey: privateKeyRef.current,
              publicKeyRaw: spkiBytes,
              wrappedMasterKey: wrappedBytes,
              wrapFormat: ALG_P256_ECDH_HKDF_V1,
            })
            unlock(masterKey)
            onPaired(data.session_token, masterKey)
            window.location.replace('/')
          }
        } catch {
          /* keep polling */
        }
      }, 1000)
    } catch {
      setErrorMsg('Could not reach the server.')
    }
  }

  if (phase === 'qr') {
    return (
      <div className="min-h-screen bg-parchment flex flex-col items-center justify-center gap-6 p-6">
        <p className="text-sm text-text-muted text-center max-w-xs">
          Scan this QR code with the Heirlooms app on your phone to complete pairing.
        </p>
        {qrDataUrl && <img src={qrDataUrl} alt="Pairing QR code" className="rounded-lg shadow-md" />}
        <WorkingDots size="sm" label="Waiting for phone…" />
        <button onClick={() => { clearInterval(pollRef.current); setPhase('enter'); setCode('') }}
          className="text-sm text-text-muted underline">
          Cancel
        </button>
      </div>
    )
  }

  if (phase === 'error') {
    return (
      <div className="min-h-screen bg-parchment flex items-center justify-center">
        <div className="text-center space-y-4 max-w-sm">
          <p className="font-serif italic text-earth">{errorMsg}</p>
          <button onClick={() => { setPhase('enter'); setCode(''); setErrorMsg('') }}
            className="text-sm text-forest underline">
            Try again
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-parchment flex items-center justify-center">
      <div className="bg-white rounded-card shadow-sm border border-forest-08 p-8 w-full max-w-sm space-y-4">
        <h1 className="font-serif text-xl text-forest">Pair with phone</h1>
        <p className="text-sm text-text-muted">
          Open Heirlooms on your phone and go to Devices &amp; Access to generate a pairing code.
        </p>
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            inputMode="numeric"
            value={code}
            onChange={e => setCode(e.target.value)}
            placeholder="Pairing code"
            className="w-full px-3 py-2 border border-forest-15 rounded-button text-sm focus:outline-none focus:ring-2 focus:ring-forest-25 tracking-widest text-center"
          />
          {errorMsg && <p className="text-sm text-earth font-serif italic">{errorMsg}</p>}
          <button
            type="submit"
            disabled={!code.trim()}
            className="w-full bg-forest text-parchment py-2 rounded-button text-sm font-medium hover:opacity-90 disabled:opacity-40 transition-opacity"
          >
            Continue
          </button>
        </form>
      </div>
    </div>
  )
}
