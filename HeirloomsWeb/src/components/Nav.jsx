import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { OliveBranchIcon } from '../brand/OliveBranchIcon'
import { useAuth } from '../AuthContext'

function HamburgerIcon() {
  return (
    <svg width="22" height="16" viewBox="0 0 22 16" fill="none" aria-hidden="true">
      <rect y="0" width="22" height="2" rx="1" fill="currentColor" />
      <rect y="7" width="22" height="2" rx="1" fill="currentColor" />
      <rect y="14" width="22" height="2" rx="1" fill="currentColor" />
    </svg>
  )
}

const navLinkClass = ({ isActive }) =>
  `text-sm font-sans transition-colors ${
    isActive
      ? 'text-forest border-b-2 border-earth pb-0.5'
      : 'text-forest-75 hover:text-forest'
  }`

export function Nav() {
  const { onSignOut, displayName } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <>
      <header className="bg-parchment border-b border-earth/20 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <NavLink to="/" className="flex items-center gap-2 no-underline">
            <OliveBranchIcon width={20} />
            <span className="font-serif italic text-[17px] text-forest">Heirlooms</span>
          </NavLink>

          {/* Desktop nav */}
          <nav className="hidden sm:flex items-center gap-8">
            <NavLink to="/" end className={navLinkClass}>Garden</NavLink>
            <NavLink to="/explore" className={navLinkClass}>Explore</NavLink>
            <NavLink to="/capsules" className={navLinkClass}>Capsules</NavLink>
            <NavLink to="/shared" className={navLinkClass}>Shared</NavLink>
            <NavLink to="/flows" className={navLinkClass}>Trellises</NavLink>
          </nav>

          <div className="hidden sm:flex items-center gap-6">
            {displayName && (
              <span className="text-sm text-text-muted font-sans">Hi, {displayName}</span>
            )}
            <NavLink to="/access" className={navLinkClass}>Access</NavLink>
            <button
              onClick={onSignOut}
              className="text-sm text-text-muted hover:text-forest transition-colors"
            >
              Log out
            </button>
          </div>

          {/* Mobile hamburger */}
          <button
            className="sm:hidden text-forest"
            onClick={() => setMenuOpen(true)}
            aria-label="Open menu"
          >
            <HamburgerIcon />
          </button>
        </div>
      </header>

      {/* Mobile slide-in panel */}
      {menuOpen && (
        <>
          <div
            className="fixed inset-0 z-40 bg-black/30"
            onClick={() => setMenuOpen(false)}
          />
          <div className="fixed top-0 right-0 bottom-0 z-50 w-4/5 max-w-xs bg-parchment shadow-xl flex flex-col p-6">
            <div className="mb-6">
              <span className="font-serif italic text-[17px] text-forest">Heirlooms</span>
            </div>
            <nav className="flex flex-col gap-5 text-base">
              <NavLink
                to="/"
                end
                className={({ isActive }) =>
                  `font-sans ${isActive ? 'text-forest border-b-2 border-earth pb-0.5 self-start' : 'text-forest-75'}`
                }
                onClick={() => setMenuOpen(false)}
              >
                Garden
              </NavLink>
              <NavLink
                to="/explore"
                className={({ isActive }) =>
                  `font-sans ${isActive ? 'text-forest border-b-2 border-earth pb-0.5 self-start' : 'text-forest-75'}`
                }
                onClick={() => setMenuOpen(false)}
              >
                Explore
              </NavLink>
              <NavLink
                to="/capsules"
                className={({ isActive }) =>
                  `font-sans ${isActive ? 'text-forest border-b-2 border-earth pb-0.5 self-start' : 'text-forest-75'}`
                }
                onClick={() => setMenuOpen(false)}
              >
                Capsules
              </NavLink>
              <NavLink
                to="/shared"
                className={({ isActive }) =>
                  `font-sans ${isActive ? 'text-forest border-b-2 border-earth pb-0.5 self-start' : 'text-forest-75'}`
                }
                onClick={() => setMenuOpen(false)}
              >
                Shared
              </NavLink>
              <NavLink
                to="/flows"
                className={({ isActive }) =>
                  `font-sans ${isActive ? 'text-forest border-b-2 border-earth pb-0.5 self-start' : 'text-forest-75'}`
                }
                onClick={() => setMenuOpen(false)}
              >
                Trellises
              </NavLink>
              <NavLink
                to="/access"
                className={({ isActive }) =>
                  `font-sans ${isActive ? 'text-forest border-b-2 border-earth pb-0.5 self-start' : 'text-forest-75'}`
                }
                onClick={() => setMenuOpen(false)}
              >
                Access
              </NavLink>
            </nav>
            <div className="mt-auto border-t border-forest-15 pt-4">
              <button
                onClick={() => { setMenuOpen(false); onSignOut() }}
                className="text-sm text-text-muted hover:text-forest transition-colors"
              >
                Log out
              </button>
            </div>
          </div>
        </>
      )}
    </>
  )
}
