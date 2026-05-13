import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthContext, useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { LoginPage } from './pages/LoginPage'
import { JoinPage } from './pages/JoinPage'
import { AccessPage } from './pages/AccessPage'
import { PairPage } from './pages/PairPage'
import { VaultUnlockPage } from './pages/VaultUnlockPage'
import { GardenPage } from './pages/GardenPage'
import { PhotoDetailPage } from './pages/PhotoDetailPage'
import { CapsulesListPage } from './pages/capsules/CapsulesListPage'
import { CapsuleDetailPage } from './pages/capsules/CapsuleDetailPage'
import { CapsuleCreatePage } from './pages/capsules/CapsuleCreatePage'
import { CompostHeapPage } from './pages/CompostHeapPage'
import { ExplorePage } from './pages/ExplorePage'
import { FlowsPage } from './pages/FlowsPage'
import { PlotJoinPage } from './pages/PlotJoinPage'
import { SharedPlotsPage } from './pages/SharedPlotsPage'
import { lock, unlock, setSharingPrivkey } from './crypto/vaultSession'
import { authLogout, authMe, API_URL } from './api'
import { unwrapMasterKeyForDevice, unwrapDekWithMasterKey, fromB64, importSharingPrivkey } from './crypto/vaultCrypto'
import { loadPairingMaterial, clearPairingMaterial } from './crypto/webPairingStore'

async function loadSharingKey(token, masterKey) {
  try {
    const r = await fetch(`${API_URL}/api/keys/sharing/me`, { headers: { 'X-Api-Key': token } })
    if (!r.ok) return
    const { wrappedPrivkey } = await r.json()
    if (!wrappedPrivkey) return
    const pkcs8Bytes = await unwrapDekWithMasterKey(fromB64(wrappedPrivkey), masterKey)
    const cryptoKey = await importSharingPrivkey(pkcs8Bytes)
    setSharingPrivkey(cryptoKey)
  } catch { /* sharing key not yet set up — fine */ }
}

const LS_TOKEN        = 'heirlooms_session_token'
const LS_USERNAME     = 'heirlooms_username'
const LS_DISPLAY_NAME = 'heirlooms_display_name'

function RequireAuth() {
  const { sessionToken, vaultUnlocked, onVaultUnlocked } = useAuth()
  const location = useLocation()
  if (!sessionToken) {
    const from = location.pathname + location.search
    return <Navigate to="/login" state={{ from }} replace />
  }
  if (!vaultUnlocked) {
    return <VaultUnlockPage apiKey={sessionToken} onUnlocked={onVaultUnlocked} />
  }
  return <AuthLayout />
}

export default function App() {
  const [sessionToken, setSessionTokenState] = useState(() => localStorage.getItem(LS_TOKEN) ?? null)
  const [username, setUsernameState] = useState(() => localStorage.getItem(LS_USERNAME) ?? null)
  const [displayName, setDisplayNameState] = useState(() => localStorage.getItem(LS_DISPLAY_NAME) ?? null)
  const [vaultUnlocked, setVaultUnlocked] = useState(false)

  // On mount: validate cached session token and auto-unlock vault from IDB pairing
  // material if available (survives page refresh without re-entering passphrase).
  useEffect(() => {
    const token = localStorage.getItem(LS_TOKEN)
    if (!token) return
    ;(async () => {
      try {
        const r = await authMe(token)
        if (r.status === 401) {
          try { await clearPairingMaterial() } catch { /* best effort */ }
          setSessionTokenState(null)
          localStorage.removeItem(LS_TOKEN)
          return
        }
        if (r.ok) {
          try {
            const data = await r.json()
            if (data?.display_name) {
              setDisplayNameState(data.display_name)
              localStorage.setItem(LS_DISPLAY_NAME, data.display_name)
            }
          } catch { /* ignore */ }
        }
        const material = await loadPairingMaterial()
        if (!material) return
        const masterKey = await unwrapMasterKeyForDevice(material.wrappedMasterKey, material.privateKey)
        unlock(masterKey)
        await loadSharingKey(token, masterKey)
        setVaultUnlocked(true)
      } catch {
        // Network error — don't force logout; let the user interact normally
      }
    })()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function setSession(token, uname, dname) {
    if (token) {
      localStorage.setItem(LS_TOKEN, token)
      setSessionTokenState(token)
    } else {
      localStorage.removeItem(LS_TOKEN)
      setSessionTokenState(null)
    }
    if (uname !== undefined) {
      uname ? localStorage.setItem(LS_USERNAME, uname) : localStorage.removeItem(LS_USERNAME)
      setUsernameState(uname ?? null)
    }
    if (dname !== undefined) {
      dname ? localStorage.setItem(LS_DISPLAY_NAME, dname) : localStorage.removeItem(LS_DISPLAY_NAME)
      setDisplayNameState(dname ?? null)
    }
  }

  function handleLogin(token, masterKey) {
    setSession(token)
    if (masterKey) {
      unlock(masterKey)
      loadSharingKey(token, masterKey).then(() => setVaultUnlocked(true))
    }
    authMe(token).then(r => r.ok ? r.json() : null).then(data => {
      if (data?.display_name) {
        setDisplayNameState(data.display_name)
        localStorage.setItem(LS_DISPLAY_NAME, data.display_name)
      }
    }).catch(() => {})
  }

  async function handleSignOut() {
    lock()
    try { await clearPairingMaterial() } catch { /* best effort */ }
    if (sessionToken) {
      try { await authLogout(sessionToken) } catch { /* best effort */ }
    }
    setSession(null, null, null)
    setVaultUnlocked(false)
  }

  return (
    <AuthContext.Provider value={{
      // sessionToken is the canonical name; apiKey is kept for backwards compat
      sessionToken,
      apiKey: sessionToken,
      username,
      displayName,
      vaultUnlocked,
      onVaultUnlocked: () => setVaultUnlocked(true),
      onSignOut: handleSignOut,
    }}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
          <Route path="/join" element={<JoinPage onJoined={handleLogin} />} />
          <Route path="/pair" element={<PairPage onPaired={handleLogin} />} />
          <Route path="/" element={<RequireAuth />}>
            <Route index element={<GardenPage />} />
            <Route path="explore" element={<ExplorePage />} />
            <Route path="photos/:id" element={<PhotoDetailPage />} />
            <Route path="capsules" element={<CapsulesListPage />} />
            <Route path="capsules/new" element={<CapsuleCreatePage />} />
            <Route path="capsules/:id" element={<CapsuleDetailPage />} />
            <Route path="compost" element={<CompostHeapPage />} />
            <Route path="flows" element={<FlowsPage />} />
            <Route path="shared" element={<SharedPlotsPage />} />
            <Route path="plots/join" element={<PlotJoinPage />} />
            <Route path="access" element={<AccessPage />} />
            <Route path="access/pair" element={<PairPage onPaired={handleLogin} />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthContext.Provider>
  )
}
