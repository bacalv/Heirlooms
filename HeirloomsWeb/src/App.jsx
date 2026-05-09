import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthContext, useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { LoginPage } from './pages/LoginPage'
import { VaultUnlockPage } from './pages/VaultUnlockPage'
import { GardenPage } from './pages/GardenPage'
import { PhotoDetailPage } from './pages/PhotoDetailPage'
import { CapsulesListPage } from './pages/capsules/CapsulesListPage'
import { CapsuleDetailPage } from './pages/capsules/CapsuleDetailPage'
import { CapsuleCreatePage } from './pages/capsules/CapsuleCreatePage'
import { CompostHeapPage } from './pages/CompostHeapPage'
import { ExplorePage } from './pages/ExplorePage'
import { lock } from './crypto/vaultSession'

function RequireAuth() {
  const { apiKey, vaultUnlocked, onVaultUnlocked } = useAuth()
  const location = useLocation()
  if (!apiKey) {
    const from = location.pathname + location.search
    return <Navigate to="/login" state={{ from }} replace />
  }
  if (!vaultUnlocked) {
    return <VaultUnlockPage apiKey={apiKey} onUnlocked={onVaultUnlocked} />
  }
  return <AuthLayout />
}

export default function App() {
  const [apiKey, setApiKey] = useState(null)
  const [vaultUnlocked, setVaultUnlocked] = useState(false)

  function handleSignOut() {
    lock()
    setApiKey(null)
    setVaultUnlocked(false)
  }

  return (
    <AuthContext.Provider value={{
      apiKey,
      vaultUnlocked,
      onVaultUnlocked: () => setVaultUnlocked(true),
      onSignOut: handleSignOut,
    }}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage onLogin={setApiKey} />} />
          <Route path="/" element={<RequireAuth />}>
            <Route index element={<GardenPage />} />
            <Route path="explore" element={<ExplorePage />} />
            <Route path="photos/:id" element={<PhotoDetailPage />} />
            <Route path="capsules" element={<CapsulesListPage />} />
            <Route path="capsules/new" element={<CapsuleCreatePage />} />
            <Route path="capsules/:id" element={<CapsuleDetailPage />} />
            <Route path="compost" element={<CompostHeapPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthContext.Provider>
  )
}
