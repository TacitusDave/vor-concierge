import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, Capability } from './context/AuthContext.jsx';
import { ProtectedRoute } from './components/ProtectedRoute.jsx';
import { Layout } from './components/Layout.jsx';
import { Login } from './pages/Login.jsx';
import { Dashboard } from './pages/Dashboard.jsx';
import { Chat } from './pages/Chat.jsx';
import { Sources } from './pages/Sources.jsx';
import { Organization } from './pages/Organization.jsx';
import { Members } from './pages/Members.jsx';
import { Admin } from './pages/Admin.jsx';

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/chat" element={<Chat />} />

            <Route element={<ProtectedRoute requireCapability={Capability.UPLOAD_DOCUMENTS} />}>
              <Route path="/sources" element={<Sources />} />
            </Route>

            <Route
              element={
                <ProtectedRoute requireAnyCapability={[Capability.MANAGE_DEPARTMENTS, Capability.MANAGE_ROLES]} />
              }
            >
              <Route path="/organization" element={<Organization />} />
            </Route>

            <Route element={<ProtectedRoute requireCapability={Capability.MANAGE_USERS} />}>
              <Route path="/members" element={<Members />} />
            </Route>

            <Route element={<ProtectedRoute platformOnly />}>
              <Route path="/admin" element={<Admin />} />
            </Route>
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  );
}

export default App;
