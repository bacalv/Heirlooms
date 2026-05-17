/**
 * BUG-023 regression tests
 *
 * Covers:
 *  - VaultUnlockPage.handleSetup skips POST /api/keys/devices when device is
 *    already registered (isVaultSetUp() === true), preventing 409 after web pairing.
 *  - VaultUnlockPage.handleSetup calls POST /api/keys/devices when device is
 *    NOT yet registered (isVaultSetUp() === false), preserving the happy path.
 *  - PairPage calls markVaultSetUp() after a successful pairing so that the
 *    subsequent passphrase setup does not attempt to re-register the device.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { VaultUnlockPage } from '../pages/VaultUnlockPage'
import { PairPage } from '../pages/PairPage'
import { markVaultSetUp } from '../crypto/deviceKeyManager'

// ---- Module mocks ------------------------------------------------------------

vi.mock('../crypto/vaultCrypto', async (importOriginal) => {
  const real = await importOriginal()
  return {
    ...real,
    generateMasterKey: vi.fn(() => new Uint8Array(32).fill(3)),
    wrapMasterKeyWithPassphrase: vi.fn(async () => ({
      envelope: new Uint8Array(80).fill(9),
      salt: new Uint8Array(16).fill(4),
      params: { m: 65536, t: 3, p: 1 },
    })),
    unwrapMasterKeyWithPassphrase: vi.fn(async () => new Uint8Array(32).fill(3)),
    wrapMasterKeyForDevice: vi.fn(async () => new Uint8Array(100).fill(6)),
    unwrapMasterKeyForDevice: vi.fn(async () => new Uint8Array(32).fill(7)),
    fromB64: vi.fn((s) => new Uint8Array(32).fill(0)),
    toB64: vi.fn((b) => 'base64stub=='),
  }
})

vi.mock('../crypto/deviceKeyManager', () => ({
  generateAndStoreKeypair: vi.fn(async () => new Uint8Array(65).fill(5)),
  getDeviceId: vi.fn(() => 'test-device-id'),
  getDeviceLabel: vi.fn(() => 'Test Browser'),
  loadPrivateKey: vi.fn(async () => null),
  isDeviceRegistered: vi.fn(async () => false),
  isVaultSetUp: vi.fn(() => false),
  markVaultSetUp: vi.fn(),
}))

vi.mock('../crypto/vaultSession', () => ({
  lock: vi.fn(),
  unlock: vi.fn(),
  isUnlocked: vi.fn(() => false),
  getMasterKey: vi.fn(),
  getSharingPrivkey: vi.fn(() => null),
  setSharingPrivkey: vi.fn(),
  thumbnailCache: new Map(),
  cacheThumbnail: vi.fn(),
}))

vi.mock('qrcode', () => ({
  default: { toDataURL: vi.fn(async () => 'data:image/png;base64,stub') },
}))

vi.mock('../crypto/webPairingStore', () => ({
  savePairingMaterial: vi.fn(async () => {}),
  loadPairingMaterial: vi.fn(async () => null),
  clearPairingMaterial: vi.fn(async () => {}),
}))

// ---- localStorage stub -------------------------------------------------------

let _ls = {}
const mockLS = {
  getItem: (k) => _ls[k] ?? null,
  setItem: (k, v) => { _ls[k] = String(v) },
  removeItem: (k) => { delete _ls[k] },
  clear: () => { _ls = {} },
}
vi.stubGlobal('localStorage', mockLS)

// ---- Helpers -----------------------------------------------------------------

import * as deviceKeyManager from '../crypto/deviceKeyManager'

function renderVaultUnlock(onUnlocked = vi.fn()) {
  return render(
    <VaultUnlockPage apiKey="test-api-key" onUnlocked={onUnlocked} />
  )
}

function pairWrapper(onPaired = vi.fn()) {
  return render(
    <MemoryRouter>
      <PairPage onPaired={onPaired} />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  _ls = {}
  global.fetch = vi.fn()
})

// ---- VaultUnlockPage BUG-023 tests ------------------------------------------

describe('VaultUnlockPage — passphrase setup (BUG-023)', () => {
  it('skips POST /api/keys/devices when device is already registered (isVaultSetUp=true)', async () => {
    // Simulate post-web-pairing or post-web-registration state
    deviceKeyManager.isVaultSetUp.mockReturnValue(true)

    global.fetch
      // probe: GET /api/keys/passphrase → 404 (no passphrase yet)
      .mockResolvedValueOnce({ ok: false, status: 404 })
      // PUT /api/keys/passphrase → 200
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({}) })
      // GET /api/keys/sharing/me (ensureSharingKey) → 404 (no sharing key)
      .mockResolvedValue({ ok: false, status: 404 })

    const onUnlocked = vi.fn()
    renderVaultUnlock(onUnlocked)

    // Wait for setup state (passphrase not set up)
    await waitFor(() => screen.getByPlaceholderText('Passphrase'))

    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'my-passphrase' } })
    fireEvent.change(screen.getByPlaceholderText('Confirm passphrase'), { target: { value: 'my-passphrase' } })
    fireEvent.click(screen.getByRole('button', { name: /save passphrase/i }))

    await waitFor(() => expect(onUnlocked).toHaveBeenCalled())

    // Device registration must NOT have been called
    const devicePostCalls = global.fetch.mock.calls.filter(
      ([url, opts]) => url?.includes('/api/keys/devices') && opts?.method === 'POST'
    )
    expect(devicePostCalls).toHaveLength(0)
  })

  it('calls POST /api/keys/devices when device is not yet registered (isVaultSetUp=false)', async () => {
    deviceKeyManager.isVaultSetUp.mockReturnValue(false)

    global.fetch
      // probe: GET /api/keys/passphrase → 404
      .mockResolvedValueOnce({ ok: false, status: 404 })
      // PUT /api/keys/passphrase → 200
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({}) })
      // POST /api/keys/devices → 201
      .mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: async () => ({ deviceId: 'test-device-id', wrappedMasterKey: 'base64stub==' }),
      })
      // GET /api/keys/sharing/me (ensureSharingKey) → 404
      .mockResolvedValue({ ok: false, status: 404 })

    const onUnlocked = vi.fn()
    renderVaultUnlock(onUnlocked)

    await waitFor(() => screen.getByPlaceholderText('Passphrase'))

    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'my-passphrase' } })
    fireEvent.change(screen.getByPlaceholderText('Confirm passphrase'), { target: { value: 'my-passphrase' } })
    fireEvent.click(screen.getByRole('button', { name: /save passphrase/i }))

    await waitFor(() => expect(onUnlocked).toHaveBeenCalled())

    const devicePostCalls = global.fetch.mock.calls.filter(
      ([url, opts]) => url?.includes('/api/keys/devices') && opts?.method === 'POST'
    )
    expect(devicePostCalls).toHaveLength(1)
    expect(deviceKeyManager.markVaultSetUp).toHaveBeenCalled()
  })
})

// ---- PairPage BUG-029 tests --------------------------------------------------

describe('PairPage — device registration after pairing (BUG-029)', () => {
  it('calls POST /api/keys/devices with session token after pairing completes', async () => {
    const onPaired = vi.fn()

    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ session_id: 'sess-1' }) })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({
          state: 'complete',
          session_token: 'pair-tok-789',
          wrapped_master_key: btoa(String.fromCharCode(...new Uint8Array(100).fill(8))),
        }),
      })
      .mockResolvedValueOnce({ ok: true, status: 201, json: async () => ({}) })

    pairWrapper(onPaired)

    fireEvent.change(screen.getByPlaceholderText('Pairing code'), { target: { value: '12345678' } })
    fireEvent.click(screen.getByRole('button', { name: /continue/i }))

    await waitFor(
      () => expect(onPaired).toHaveBeenCalledWith('pair-tok-789', expect.any(Uint8Array)),
      { timeout: 4000 },
    )

    const devicePostCalls = global.fetch.mock.calls.filter(
      ([url, opts]) => url?.includes('/api/keys/devices') && opts?.method === 'POST',
    )
    expect(devicePostCalls).toHaveLength(1)
    const [, opts] = devicePostCalls[0]
    expect(opts.headers['X-Api-Key']).toBe('pair-tok-789')
    const body = JSON.parse(opts.body)
    expect(body.deviceKind).toBe('web')
    expect(body.pubkeyFormat).toBe('p256-spki')
    expect(body.deviceId).toBe('test-device-id')
  }, 10000)
})

// ---- PairPage BUG-023 tests --------------------------------------------------

describe('PairPage — markVaultSetUp called after pairing (BUG-023)', () => {
  it('calls markVaultSetUp() after a successful pairing completes', async () => {
    const onPaired = vi.fn()

    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ session_id: 'sess-1' }) })
      .mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          state: 'complete',
          session_token: 'pair-tok',
          wrapped_master_key: btoa(String.fromCharCode(...new Uint8Array(100).fill(8))),
        }),
      })

    pairWrapper(onPaired)

    fireEvent.change(screen.getByPlaceholderText('Pairing code'), { target: { value: '12345678' } })
    fireEvent.click(screen.getByRole('button', { name: /continue/i }))

    await waitFor(() => expect(onPaired).toHaveBeenCalledWith('pair-tok', expect.any(Uint8Array)), { timeout: 4000 })

    expect(markVaultSetUp).toHaveBeenCalled()
  }, 10000)
})
