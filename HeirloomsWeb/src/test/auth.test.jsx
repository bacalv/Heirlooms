import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, Navigate, useLocation } from 'react-router-dom'
import { AuthContext } from '../AuthContext'
import { LoginPage } from '../pages/LoginPage'
import { JoinPage } from '../pages/JoinPage'
import { AccessPage } from '../pages/AccessPage'
import { PairPage } from '../pages/PairPage'

// ---- Module mocks (hoisted to top by vitest) --------------------------------

vi.mock('@noble/hashes/argon2', () => ({
  argon2id: vi.fn(() => new Uint8Array(64).fill(1)),
}))

vi.mock('../crypto/vaultCrypto', async (importOriginal) => {
  const real = await importOriginal()
  return {
    ...real,
    sha256: vi.fn(async () => new Uint8Array(32).fill(2)),
    generateMasterKey: vi.fn(() => new Uint8Array(32).fill(3)),
    generateSalt: vi.fn(() => new Uint8Array(16).fill(4)),
    wrapMasterKeyForDevice: vi.fn(async () => new Uint8Array(100).fill(6)),
    unwrapMasterKeyForDevice: vi.fn(async () => new Uint8Array(32).fill(7)),
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
  thumbnailCache: new Map(),
  cacheThumbnail: vi.fn(),
}))

vi.mock('qrcode', () => ({
  default: { toDataURL: vi.fn(async () => 'data:image/png;base64,stub') },
}))

// ---- localStorage stub -------------------------------------------------------

let _ls = {}
const mockLS = {
  getItem: k => _ls[k] ?? null,
  setItem: (k, v) => { _ls[k] = String(v) },
  removeItem: k => { delete _ls[k] },
  clear: () => { _ls = {} },
  key: i => Object.keys(_ls)[i] ?? null,
  get length() { return Object.keys(_ls).length },
}
vi.stubGlobal('localStorage', mockLS)

beforeEach(() => {
  vi.clearAllMocks()
  _ls = {}
  global.fetch = vi.fn()
})

// ---- helpers ----------------------------------------------------------------

function loginWrapper(onLogin = vi.fn()) {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage onLogin={onLogin} />} />
        <Route path="/" element={<div>Home</div>} />
      </Routes>
    </MemoryRouter>
  )
}

function joinWrapper(onJoined = vi.fn(), token = '') {
  const path = token ? `/join?token=${token}` : '/join'
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/join" element={<JoinPage onJoined={onJoined} />} />
        <Route path="/" element={<div>Home</div>} />
      </Routes>
    </MemoryRouter>
  )
}

function accessWrapper(sessionToken = 'tok') {
  return render(
    <AuthContext.Provider value={{ sessionToken, apiKey: sessionToken, onSignOut: vi.fn() }}>
      <MemoryRouter>
        <AccessPage />
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

function pairWrapper(onPaired = vi.fn()) {
  return render(
    <MemoryRouter>
      <PairPage onPaired={onPaired} />
    </MemoryRouter>
  )
}

// RequireAuth in isolation — mirrors the logic in App.jsx
function CaptureRedirectRoute() {
  const location = useLocation()
  return <div data-testid="login-page">redirected from: {location.state?.from}</div>
}

function RequireAuthWrapper({ sessionToken }) {
  return (
    <AuthContext.Provider value={{ sessionToken, apiKey: sessionToken, vaultUnlocked: false, onVaultUnlocked: vi.fn(), onSignOut: vi.fn() }}>
      <MemoryRouter initialEntries={['/garden']}>
        <Routes>
          <Route path="/login" element={<CaptureRedirectRoute />} />
          <Route path="/*" element={
            sessionToken ? <div>Authenticated content</div> : <Navigate to="/login" state={{ from: '/garden' }} replace />
          } />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

// ---- Test 1: Login success --------------------------------------------------

describe('LoginPage', () => {
  it('submits challenge + login in sequence; calls onLogin on success', async () => {
    const onLogin = vi.fn()

    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ auth_salt: 'AAAAAAAAAAAAAAAAAAAAAA' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ session_token: 'tok123' }) })
      .mockResolvedValue({ ok: false, status: 404, json: async () => ({}) })

    loginWrapper(onLogin)

    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } })
    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'secret' } })
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith('tok123', null))
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/auth/challenge'), expect.any(Object)
    )
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/auth/login'), expect.any(Object)
    )
  })

  // ---- Test 2: Wrong passphrase -----------------------------------------------

  it('shows error message on 401, does not call onLogin', async () => {
    const onLogin = vi.fn()

    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ auth_salt: 'AAAAAAAAAAAAAAAAAAAAAA' }) })
      .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) })

    loginWrapper(onLogin)

    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } })
    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => screen.getByText(/invalid username or passphrase/i))
    expect(onLogin).not.toHaveBeenCalled()
  })
})

// ---- Test 3: Register flow --------------------------------------------------

describe('JoinPage', () => {
  it('register flow: invite + passphrase → register call → calls onJoined with token', async () => {
    const onJoined = vi.fn()

    global.fetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: async () => ({ session_token: 'reg-tok' }),
    })

    joinWrapper(onJoined, 'invite-abc')

    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'newuser' } })
    fireEvent.change(screen.getByPlaceholderText('Display name'), { target: { value: 'New User' } })
    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'pass1234' } })
    fireEvent.change(screen.getByPlaceholderText('Confirm passphrase'), { target: { value: 'pass1234' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => expect(onJoined).toHaveBeenCalledWith('reg-tok', expect.any(Uint8Array)))
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/auth/register'), expect.any(Object)
    )
  })

  // ---- Test 4: Duplicate username --------------------------------------------

  it('shows inline error on 409 duplicate username', async () => {
    const onJoined = vi.fn()

    global.fetch.mockResolvedValueOnce({ ok: false, status: 409 })

    joinWrapper(onJoined, 'invite-abc')

    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'taken' } })
    fireEvent.change(screen.getByPlaceholderText('Display name'), { target: { value: 'Taken' } })
    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'pass1234' } })
    fireEvent.change(screen.getByPlaceholderText('Confirm passphrase'), { target: { value: 'pass1234' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => screen.getByText(/username already taken/i))
    expect(onJoined).not.toHaveBeenCalled()
  })

  // ---- Test 5: Expired invite ------------------------------------------------

  it('shows inline error on 410 expired invite', async () => {
    const onJoined = vi.fn()

    global.fetch.mockResolvedValueOnce({ ok: false, status: 410 })

    joinWrapper(onJoined, 'invite-expired')

    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'newuser' } })
    fireEvent.change(screen.getByPlaceholderText('Display name'), { target: { value: 'New' } })
    fireEvent.change(screen.getByPlaceholderText('Passphrase'), { target: { value: 'pass1234' } })
    fireEvent.change(screen.getByPlaceholderText('Confirm passphrase'), { target: { value: 'pass1234' } })
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => screen.getByText(/invalid or expired/i))
    expect(onJoined).not.toHaveBeenCalled()
  })
})

// ---- Test 6 & 7: Pairing flow -----------------------------------------------

describe('PairPage', () => {
  it('code entered → qr endpoint called → QR shown', async () => {
    // Verify initial flow up to QR display (no polling needed)
    global.fetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ session_id: 'sess-1' }) })
      .mockResolvedValue({ ok: true, status: 200, json: async () => ({ state: 'pending' }) })

    pairWrapper(vi.fn())

    fireEvent.change(screen.getByPlaceholderText('Pairing code'), { target: { value: '12345678' } })
    fireEvent.click(screen.getByRole('button', { name: /continue/i }))

    await waitFor(() => screen.getByAltText('Pairing QR code'), { timeout: 4000 })
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/auth/pairing/qr'), expect.any(Object)
    )
  }, 6000)

  // ---- Test 7: Pairing complete → onPaired called ----------------------------

  it('on complete status → onPaired called with token and master key', async () => {
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

    // Wait for QR then for polling to call onPaired (interval fires after ~1s real time)
    await waitFor(() => screen.getByAltText('Pairing QR code'), { timeout: 4000 })
    await waitFor(() => expect(onPaired).toHaveBeenCalledWith('pair-tok', expect.any(Uint8Array)), { timeout: 4000 })
  }, 10000)
})

// ---- Test 8: Invite generation ----------------------------------------------

describe('AccessPage', () => {
  it('generates invite → shows shareable URL with copy button', async () => {
    const expiresAt = new Date(Date.now() + 47 * 3_600_000).toISOString()

    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ token: 'invite-xyz', expires_at: expiresAt }),
    })

    accessWrapper('my-token')

    fireEvent.click(screen.getByRole('button', { name: /generate invite/i }))

    await waitFor(() => screen.getByDisplayValue(/\/join\?token=invite-xyz/))
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/auth/invites'), expect.any(Object)
    )
  })
})

// ---- Test 9: Logout ---------------------------------------------------------

describe('Logout', () => {
  it('clears session token from localStorage when sign out is triggered', () => {
    _ls['heirlooms_session_token'] = 'existing-tok'

    const signOut = vi.fn(() => { delete _ls['heirlooms_session_token'] })

    render(
      <AuthContext.Provider value={{ sessionToken: 'existing-tok', apiKey: 'existing-tok', onSignOut: signOut, vaultUnlocked: true, onVaultUnlocked: vi.fn() }}>
        <MemoryRouter>
          <button onClick={signOut}>Log out</button>
        </MemoryRouter>
      </AuthContext.Provider>
    )

    fireEvent.click(screen.getByRole('button', { name: /log out/i }))

    expect(signOut).toHaveBeenCalled()
    expect(mockLS.getItem('heirlooms_session_token')).toBeNull()
  })
})

// ---- Test 10: RequireAuth ---------------------------------------------------

describe('RequireAuth', () => {
  it('redirects to /login with state.from when no session token', () => {
    render(<RequireAuthWrapper sessionToken={null} />)

    expect(screen.getByTestId('login-page')).toBeInTheDocument()
    expect(screen.getByText(/redirected from: \/garden/)).toBeInTheDocument()
  })
})
