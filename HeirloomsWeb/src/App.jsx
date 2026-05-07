import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthContext } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { LoginPage } from './pages/LoginPage'
import { GardenPage } from './pages/GardenPage'
import { PhotoDetailPage } from './pages/PhotoDetailPage'
import { CapsulesListPage } from './pages/capsules/CapsulesListPage'
import { CapsuleDetailPage } from './pages/capsules/CapsuleDetailPage'
import { CapsuleCreatePage } from './pages/capsules/CapsuleCreatePage'

export default function App() {
  const [apiKey, setApiKey] = useState(null)

  return (
    <AuthContext.Provider value={{ apiKey, onSignOut: () => setApiKey(null) }}>
      <BrowserRouter>
        <Routes>
          <Route
            path="/login"
            element={apiKey ? <Navigate to="/" replace /> : <LoginPage onLogin={setApiKey} />}
          />
          <Route
            path="/"
            element={!apiKey ? <Navigate to="/login" replace /> : <AuthLayout />}
          >
            <Route index element={<GardenPage />} />
            <Route path="photos/:id" element={<PhotoDetailPage />} />
            <Route path="capsules" element={<CapsulesListPage />} />
            <Route path="capsules/new" element={<CapsuleCreatePage />} />
            <Route path="capsules/:id" element={<CapsuleDetailPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthContext.Provider>
  )
}
