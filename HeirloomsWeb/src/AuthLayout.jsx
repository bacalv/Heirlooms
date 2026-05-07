import { Outlet } from 'react-router-dom'
import { Nav } from './components/Nav'

export function AuthLayout() {
  return (
    <div className="min-h-screen bg-parchment">
      <Nav />
      <Outlet />
    </div>
  )
}
