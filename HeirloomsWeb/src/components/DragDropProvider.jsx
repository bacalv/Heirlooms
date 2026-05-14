import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'

/**
 * DragDropContext — exposed so any page can trigger the same upload flow
 * that the global overlay uses.
 *
 * The `onFiles` callback is provided by App (wraps encryptAndUpload from
 * GardenPage). Pages that want paste upload call `triggerUpload` directly.
 */
export const DragDropContext = createContext(null)

export function useDragDrop() {
  return useContext(DragDropContext)
}

/**
 * DragDropProvider
 *
 * Renders a full-screen overlay when files are dragged over the window.
 * Calls `onFiles(fileArray)` when files are dropped.
 *
 * dragleave fires on every child element, so we use a counter (dragDepth)
 * rather than checking relatedTarget — the counter approach is simpler and
 * works cross-browser, including when relatedTarget is null.
 */
export function DragDropProvider({ onFiles, children }) {
  const [dragging, setDragging] = useState(false)
  const dragDepth = useRef(0)

  const handleDragEnter = useCallback((e) => {
    // Only react to file drags — ignore other drag types (text, links, etc.)
    if (!e.dataTransfer?.types?.includes('Files')) return
    e.preventDefault()
    dragDepth.current += 1
    if (dragDepth.current === 1) setDragging(true)
  }, [])

  const handleDragOver = useCallback((e) => {
    if (!e.dataTransfer?.types?.includes('Files')) return
    e.preventDefault()
    // Tell the browser we'll accept a copy, not a move
    e.dataTransfer.dropEffect = 'copy'
  }, [])

  const handleDragLeave = useCallback((e) => {
    if (!e.dataTransfer?.types?.includes('Files')) return
    dragDepth.current -= 1
    if (dragDepth.current <= 0) {
      dragDepth.current = 0
      setDragging(false)
    }
  }, [])

  const handleDrop = useCallback((e) => {
    e.preventDefault()
    dragDepth.current = 0
    setDragging(false)
    const files = Array.from(e.dataTransfer?.files ?? []).filter(
      (f) => f.type.startsWith('image/') || f.type.startsWith('video/')
    )
    if (files.length > 0 && onFiles) onFiles(files)
  }, [onFiles])

  useEffect(() => {
    window.addEventListener('dragenter', handleDragEnter)
    window.addEventListener('dragover', handleDragOver)
    window.addEventListener('dragleave', handleDragLeave)
    window.addEventListener('drop', handleDrop)
    return () => {
      window.removeEventListener('dragenter', handleDragEnter)
      window.removeEventListener('dragover', handleDragOver)
      window.removeEventListener('dragleave', handleDragLeave)
      window.removeEventListener('drop', handleDrop)
    }
  }, [handleDragEnter, handleDragOver, handleDragLeave, handleDrop])

  return (
    <DragDropContext.Provider value={{ triggerUpload: onFiles }}>
      {children}

      {dragging && (
        <div
          className="fixed inset-0 z-50 flex flex-col items-center justify-center pointer-events-none"
          style={{ background: 'rgba(63,79,51,0.72)', backdropFilter: 'blur(4px)' }}
          aria-hidden="true"
        >
          <svg
            className="w-16 h-16 text-parchment mb-4 opacity-90"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"
            />
          </svg>
          <p className="font-serif italic text-parchment text-2xl tracking-wide">
            Drop files here to upload
          </p>
          <p className="text-parchment/70 text-sm font-sans mt-2">
            Images and videos will be encrypted and planted in your garden
          </p>
        </div>
      )}
    </DragDropContext.Provider>
  )
}
