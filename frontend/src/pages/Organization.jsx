import { useEffect, useState } from 'react';
import { del, get, post } from '../api/client.js';
import { Capability, useAuth } from '../context/AuthContext.jsx';

function capabilityLabel(name) {
  return name
    .split('_')
    .map((w) => w[0] + w.slice(1).toLowerCase())
    .join(' ');
}

export function Organization() {
  const { hasCapability } = useAuth();
  const canManageDepartments = hasCapability(Capability.MANAGE_DEPARTMENTS);
  const canManageRoles = hasCapability(Capability.MANAGE_ROLES);

  const [departments, setDepartments] = useState([]);
  const [roles, setRoles] = useState([]);
  const [capabilities, setCapabilities] = useState([]);

  async function loadAll() {
    const [depts, rls, caps] = await Promise.all([
      get('/departments'),
      get('/roles'),
      get('/roles/capabilities'),
    ]);
    setDepartments(depts);
    setRoles(rls);
    setCapabilities(caps);
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load on mount
    loadAll();
  }, []);

  return (
    <div className="h-full overflow-y-auto bg-ink-950 p-8">
      <h1 className="mb-6 text-lg font-semibold text-slate-100">Organization Structure</h1>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <DepartmentsPanel
          departments={departments}
          canManage={canManageDepartments}
          onChanged={loadAll}
        />
        <RolesPanel
          roles={roles}
          capabilities={capabilities}
          canManage={canManageRoles}
          onChanged={loadAll}
        />
      </div>
    </div>
  );
}

function buildDepartmentTree(departments) {
  const byParent = new Map();
  for (const d of departments) {
    const key = d.parentDepartmentId ?? null;
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key).push(d);
  }
  for (const list of byParent.values()) {
    list.sort((a, b) => a.name.localeCompare(b.name));
  }
  return byParent;
}

function DepartmentNode({ department, byParent, depth, canManage, onDelete, expanded, onToggle }) {
  const children = byParent.get(department.id) ?? [];
  const hasChildren = children.length > 0;
  const isExpanded = expanded.has(department.id);

  return (
    <div>
      <div
        className="group flex items-center justify-between rounded-md py-1.5 pr-2 text-sm hover:bg-ink-800/60"
        style={{ paddingLeft: `${depth * 20 + 8}px` }}
      >
        <button
          onClick={() => hasChildren && onToggle(department.id)}
          className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
        >
          <span className={`w-3 shrink-0 text-slate-600 ${hasChildren ? '' : 'invisible'}`}>
            {isExpanded ? '▾' : '▸'}
          </span>
          <span className="shrink-0 text-slate-500">📁</span>
          <span className="truncate text-slate-200">{department.name}</span>
        </button>
        {canManage && (
          <button
            onClick={() => onDelete(department.id)}
            className="ml-2 shrink-0 text-xs text-red-400 opacity-0 hover:underline group-hover:opacity-100"
          >
            Delete
          </button>
        )}
      </div>
      {hasChildren && isExpanded && (
        <div>
          {children.map((child) => (
            <DepartmentNode
              key={child.id}
              department={child}
              byParent={byParent}
              depth={depth + 1}
              canManage={canManage}
              onDelete={onDelete}
              expanded={expanded}
              onToggle={onToggle}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function DepartmentsPanel({ departments, canManage, onChanged }) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [expanded, setExpanded] = useState(() => new Set());

  const byParent = buildDepartmentTree(departments);
  const roots = byParent.get(null) ?? [];

  function toggleExpanded(id) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!name.trim()) return;
    setError('');
    setSubmitting(true);
    try {
      await post('/departments', { name: name.trim(), parentDepartmentId: parentId || null });
      setName('');
      if (parentId) setExpanded((prev) => new Set(prev).add(Number(parentId)));
      setParentId('');
      await onChanged();
    } catch (err) {
      setError(err.message || 'Failed to create department');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this department?')) return;
    try {
      await del(`/departments/${id}`);
      await onChanged();
    } catch (err) {
      alert(err.message || 'Failed to delete department');
    }
  }

  return (
    <div className="rounded-xl border border-ink-800 bg-ink-900 p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-200">Departments</h2>
      <div className="mb-4">
        {roots.map((d) => (
          <DepartmentNode
            key={d.id}
            department={d}
            byParent={byParent}
            depth={0}
            canManage={canManage}
            onDelete={handleDelete}
            expanded={expanded}
            onToggle={toggleExpanded}
          />
        ))}
        {departments.length === 0 && <p className="py-3 text-sm text-slate-600">No departments yet.</p>}
      </div>

      {canManage && (
        <form onSubmit={handleCreate} className="space-y-2 border-t border-ink-800 pt-4">
          <div className="flex gap-2">
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Department name"
              className="flex-1 rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <select
              value={parentId}
              onChange={(e) => setParentId(e.target.value)}
              className="rounded-md border border-ink-700 bg-ink-950 px-2 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">Top-level</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>under {d.name}</option>
              ))}
            </select>
            <button
              type="submit"
              disabled={submitting}
              className="rounded-md bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-500 disabled:opacity-50"
            >
              Add
            </button>
          </div>
          {error && <p className="text-sm text-red-400">{error}</p>}
        </form>
      )}
    </div>
  );
}

function RolesPanel({ roles, capabilities, canManage, onChanged }) {
  const [name, setName] = useState('');
  const [hierarchyLevel, setHierarchyLevel] = useState(10);
  const [selected, setSelected] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function toggleCapability(cap) {
    setSelected((prev) => (prev.includes(cap) ? prev.filter((c) => c !== cap) : [...prev, cap]));
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!name.trim()) return;
    setError('');
    setSubmitting(true);
    try {
      await post('/roles', { name: name.trim(), hierarchyLevel: Number(hierarchyLevel), capabilities: selected });
      setName('');
      setHierarchyLevel(10);
      setSelected([]);
      await onChanged();
    } catch (err) {
      setError(err.message || 'Failed to create role');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this role? Members must be reassigned first.')) return;
    try {
      await del(`/roles/${id}`);
      await onChanged();
    } catch (err) {
      alert(err.message || 'Failed to delete role');
    }
  }

  return (
    <div className="rounded-xl border border-ink-800 bg-ink-900 p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-200">Roles</h2>
      <div className="mb-4 divide-y divide-ink-800">
        {[...roles].sort((a, b) => b.hierarchyLevel - a.hierarchyLevel).map((r) => (
          <div key={r.id} className="py-2 text-sm">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-slate-200">{r.name}</span>
                <span className="ml-2 text-xs text-slate-500">level {r.hierarchyLevel}</span>
              </div>
              {canManage && !r.isDefault && (
                <button onClick={() => handleDelete(r.id)} className="text-xs text-red-400 hover:underline">
                  Delete
                </button>
              )}
            </div>
            <div className="mt-1 flex flex-wrap gap-1">
              {capabilities
                .filter((cap) => (r.capabilityMask & Capability[cap]) !== 0)
                .map((cap) => (
                  <span key={cap} className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400">
                    {capabilityLabel(cap)}
                  </span>
                ))}
            </div>
          </div>
        ))}
        {roles.length === 0 && <p className="py-3 text-sm text-slate-600">No roles yet.</p>}
      </div>

      {canManage && (
        <form onSubmit={handleCreate} className="space-y-3 border-t border-ink-800 pt-4">
          <div className="flex gap-2">
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Role name"
              className="flex-1 rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <input
              type="number"
              value={hierarchyLevel}
              onChange={(e) => setHierarchyLevel(e.target.value)}
              className="w-24 rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              title="Hierarchy level — higher is more senior"
            />
          </div>
          <div className="flex flex-wrap gap-2">
            {capabilities.map((cap) => (
              <button
                type="button"
                key={cap}
                onClick={() => toggleCapability(cap)}
                className={`rounded-full border px-3 py-1 text-xs ${
                  selected.includes(cap)
                    ? 'border-brand-500 bg-brand-500/10 text-brand-400'
                    : 'border-ink-700 text-slate-500'
                }`}
              >
                {capabilityLabel(cap)}
              </button>
            ))}
          </div>
          {error && <p className="text-sm text-red-400">{error}</p>}
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-500 disabled:opacity-50"
          >
            Add role
          </button>
        </form>
      )}
    </div>
  );
}
