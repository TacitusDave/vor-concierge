import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/**
 * platformOnly gates VOR's own platform-operator surface (e.g. /admin).
 * requireCapability gates a single org-scoped capability (e.g. Capability.UPLOAD_DOCUMENTS for /sources).
 * requireAnyCapability gates on holding at least one of several capabilities (e.g. /organization).
 */
export function ProtectedRoute({ platformOnly = false, requireCapability = null, requireAnyCapability = null }) {
  const { user, loading, isPlatformOperator, hasCapability } = useAuth();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center text-slate-400">
        Loading&hellip;
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (platformOnly && !isPlatformOperator) {
    return <Navigate to="/chat" replace />;
  }
  if (requireCapability && !hasCapability(requireCapability)) {
    return <Navigate to="/chat" replace />;
  }
  if (requireAnyCapability && !requireAnyCapability.some((bit) => hasCapability(bit))) {
    return <Navigate to="/chat" replace />;
  }
  return <Outlet />;
}
