import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Capability, useAuth } from '../context/AuthContext.jsx';
import { Logo } from './Logo.jsx';

const navItemClass = ({ isActive }) =>
  `rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-600 text-white' : 'text-slate-400 hover:text-slate-100'
  }`;

export function Layout() {
  const { user, isPlatformOperator, hasCapability, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="flex h-screen flex-col bg-ink-950">
      <header className="flex shrink-0 items-center justify-between border-b border-ink-800 bg-ink-900 px-5 py-3">
        <div className="flex items-center gap-6">
          <Logo iconSize="h-7 w-7" textSize="text-base" />
          <nav className="flex items-center gap-1">
            <NavLink to="/dashboard" className={navItemClass}>
              Dashboard
            </NavLink>
            <NavLink to="/chat" className={navItemClass}>
              Chat
            </NavLink>
            {hasCapability(Capability.UPLOAD_DOCUMENTS) && (
              <NavLink to="/sources" className={navItemClass}>
                Sources
              </NavLink>
            )}
            {(hasCapability(Capability.MANAGE_DEPARTMENTS) || hasCapability(Capability.MANAGE_ROLES)) && (
              <NavLink to="/organization" className={navItemClass}>
                Organization
              </NavLink>
            )}
            {hasCapability(Capability.MANAGE_USERS) && (
              <NavLink to="/members" className={navItemClass}>
                Members
              </NavLink>
            )}
            {isPlatformOperator && (
              <NavLink to="/admin" className={navItemClass}>
                Admin
              </NavLink>
            )}
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-slate-400">{user?.username}</span>
          <button
            onClick={handleLogout}
            className="rounded-md px-3 py-1.5 text-sm text-slate-400 hover:bg-ink-800 hover:text-slate-100"
          >
            Sign out
          </button>
        </div>
      </header>
      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  );
}
