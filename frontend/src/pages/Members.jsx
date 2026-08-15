import { useEffect, useState } from 'react';
import { get, post, put } from '../api/client.js';
import { Capability, useAuth } from '../context/AuthContext.jsx';

export function Members() {
  const { hasCapability } = useAuth();
  const canInvite = hasCapability(Capability.INVITE_USERS);

  const [members, setMembers] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [roles, setRoles] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [lastInvite, setLastInvite] = useState(null);

  async function loadAll() {
    const [mem, depts, rls] = await Promise.all([
      get('/users'),
      get('/departments'),
      get('/roles'),
    ]);
    setMembers(mem);
    setDepartments(depts);
    setRoles(rls);
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load on mount
    loadAll();
  }, []);

  async function handleInvite(payload) {
    const result = await post('/users', payload);
    setLastInvite({ email: payload.email, password: result.temporaryPassword });
    setModalOpen(false);
    await loadAll();
  }

  async function updateMembership(userId, departmentId, roleId) {
    await put(`/users/${userId}/membership`, { departmentId: departmentId || null, roleId: Number(roleId) });
    await loadAll();
  }

  async function toggleEnabled(userId, enabled) {
    await put(`/users/${userId}/enabled`, { enabled: !enabled });
    await loadAll();
  }

  return (
    <div className="h-full overflow-y-auto bg-ink-950 p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-100">Team Members</h1>
        {canInvite && (
          <button
            onClick={() => setModalOpen(true)}
            className="rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-500"
          >
            Invite Member
          </button>
        )}
      </div>

      {lastInvite && (
        <div className="mb-6 rounded-md border border-brand-700 bg-brand-500/10 p-4 text-sm text-slate-200">
          Invited <span className="font-mono">{lastInvite.email}</span> — temporary password (shown once, no email
          provider is wired up yet, share this manually):{' '}
          <span className="font-mono text-brand-400">{lastInvite.password}</span>
        </div>
      )}

      <div className="overflow-hidden rounded-xl border border-ink-800 bg-ink-900">
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
            <tr className="border-b border-ink-800">
              <th className="px-4 py-3 font-medium">Username</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Department</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-800">
            {members.map((m) => (
              <tr key={m.id}>
                <td className="px-4 py-3 font-medium text-slate-200">{m.username}</td>
                <td className="px-4 py-3 text-slate-400">{m.email}</td>
                <td className="px-4 py-3">
                  <select
                    defaultValue={m.departmentId ?? ''}
                    onChange={(e) => updateMembership(m.id, e.target.value, m.roleId)}
                    className="rounded-md border border-ink-700 bg-ink-950 px-2 py-1 text-xs text-slate-200"
                  >
                    <option value="">Org-wide</option>
                    {departments.map((d) => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))}
                  </select>
                </td>
                <td className="px-4 py-3">
                  <select
                    defaultValue={m.roleId}
                    onChange={(e) => updateMembership(m.id, m.departmentId, e.target.value)}
                    className="rounded-md border border-ink-700 bg-ink-950 px-2 py-1 text-xs text-slate-200"
                  >
                    {roles.map((r) => (
                      <option key={r.id} value={r.id}>{r.name}</option>
                    ))}
                  </select>
                </td>
                <td className="px-4 py-3">
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      m.enabled ? 'bg-brand-500/15 text-brand-400' : 'bg-slate-500/15 text-slate-400'
                    }`}
                  >
                    {m.enabled ? 'Active' : 'Disabled'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => toggleEnabled(m.id, m.enabled)}
                    className="text-xs font-medium text-slate-400 hover:underline"
                  >
                    {m.enabled ? 'Disable' : 'Enable'}
                  </button>
                </td>
              </tr>
            ))}
            {members.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-slate-600">
                  No members yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {modalOpen && (
        <InviteModal
          departments={departments}
          roles={roles}
          onClose={() => setModalOpen(false)}
          onInvite={handleInvite}
        />
      )}
    </div>
  );
}

function InviteModal({ departments, roles, onClose, onInvite }) {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [roleId, setRoleId] = useState(roles[0]?.id ?? '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!username.trim() || !email.trim() || !roleId) return;
    setError('');
    setSubmitting(true);
    try {
      await onInvite({ username: username.trim(), email: email.trim(), departmentId: departmentId || null, roleId: Number(roleId) });
    } catch (err) {
      setError(err.message || 'Failed to invite member');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-md rounded-xl border border-ink-800 bg-ink-900 p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-slate-100">Invite Member</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm text-slate-400">Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-slate-400">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-slate-400">Department</label>
            <select
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">Org-wide</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm text-slate-400">Role</label>
            <select
              value={roleId}
              onChange={(e) => setRoleId(e.target.value)}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              {roles.map((r) => (
                <option key={r.id} value={r.id}>{r.name}</option>
              ))}
            </select>
          </div>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md px-4 py-2 text-sm font-medium text-slate-400 hover:bg-ink-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-500 disabled:opacity-50"
            >
              {submitting ? 'Inviting…' : 'Invite'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
