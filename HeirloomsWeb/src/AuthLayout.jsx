import { useCallback, useRef, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Nav } from './components/Nav'
import { DragDropProvider } from './components/DragDropProvider'
import { useAuth } from './AuthContext'
import { encryptAndUpload } from './pages/GardenPage'

/**
 * AuthLayout — wraps all authenticated pages.
 *
 * Hosts the global drag+drop upload provider so any screen in the app accepts
 * file drops without changing the current page.
 */
export function AuthLayout() {
  const { apiKey } = useAuth()
  const [uploadStatus, setUploadStatus] = useState('')
  const uploadingRef = useRef(false)

  const handleFiles = useCallback(async (files) => {
    if (uploadingRef.current) return
    uploadingRef.current = true
    for (const file of files) {
      try {
        const result = await encryptAndUpload(file, apiKey, setUploadStatus)
        if (result?.duplicate) {
          setUploadStatus('Already in your garden')
          await new Promise((r) => setTimeout(r, 2000))
        } else {
          setUploadStatus('Done')
          await new Promise((r) => setTimeout(r, 1200))
        }
      } catch {
        setUploadStatus(`Couldn't upload "${file.name}"`)
        await new Promise((r) => setTimeout(r, 3000))
      }
    }
    setUploadStatus('')
    uploadingRef.current = false
  }, [apiKey])

  return (
    <DragDropProvider onFiles={handleFiles}>
      <div className="min-h-screen bg-parchment">
        <Nav />
        {uploadStatus && (
          <div className="fixed bottom-4 right-4 z-40 bg-forest text-parchment text-xs font-sans px-3 py-2 rounded shadow-lg pointer-events-none">
            {uploadStatus}
          </div>
        )}
        <Outlet />
      </div>
    </DragDropProvider>
  )
}
