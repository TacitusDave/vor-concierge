import { useEffect, useRef, useState } from 'react';
import { del, get, post, upload } from '../api/client.js';

const STATUS_STYLES = {
  PENDING: 'bg-slate-500/15 text-slate-400',
  PROCESSING: 'bg-amber-500/15 text-amber-400',
  SYNCED: 'bg-brand-500/15 text-brand-400',
  ERROR: 'bg-red-500/15 text-red-400',
};

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let value = bytes;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}

function levelLabel(level, roles) {
  if (!level) return 'Everyone';
  const match = roles.find((r) => r.hierarchyLevel === level);
  return match ? `${match.name}+` : `Level ${level}+`;
}

function formatDate(iso) {
  return new Date(iso).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function Sources() {
  const [documents, setDocuments] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [roles, setRoles] = useState([]);
  const [progress, setProgress] = useState({});
  const [modalOpen, setModalOpen] = useState(false);
  const [error, setError] = useState('');
  const [viewMode, setViewMode] = useState('table');
  const [selectedId, setSelectedId] = useState(null);
  const eventSourcesRef = useRef({});

  async function loadDocuments() {
    const list = await get('/sources');
    setDocuments(list);
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load on mount
    loadDocuments();
    get('/departments').then(setDepartments).catch(() => {});
    get('/roles').then(setRoles).catch(() => {});
    const sources = eventSourcesRef.current;
    return () => {
      Object.values(sources).forEach((es) => es.close());
    };
  }, []);

  function trackProgress(docId) {
    if (eventSourcesRef.current[docId]) return;
    const es = new EventSource(`/api/v1/sources/${docId}/progress`);
    eventSourcesRef.current[docId] = es;
    es.addEventListener('progress', (evt) => {
      try {
        const data = JSON.parse(evt.data);
        setProgress((prev) => ({ ...prev, [docId]: data }));
        if (data.status === 'SYNCED' || data.status === 'ERROR') {
          es.close();
          delete eventSourcesRef.current[docId];
          loadDocuments();
        }
      } catch {
        /* ignore malformed frame */
      }
    });
    es.onerror = () => {
      es.close();
      delete eventSourcesRef.current[docId];
    };
  }

  async function handleUpload({ file, title, departmentId, minimumRoleLevel }) {
    setError('');
    const formData = new FormData();
    formData.append('file', file);
    if (title) formData.append('title', title);
    if (departmentId) formData.append('departmentId', String(departmentId));
    formData.append('minimumRoleLevel', String(minimumRoleLevel));
    try {
      const result = await upload('/sources/upload', formData);
      setModalOpen(false);
      await loadDocuments();
      trackProgress(result.documentId);
    } catch (err) {
      setError(err.message || 'Upload failed');
    }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this document and all of its indexed chunks?')) return;
    await del(`/sources/${id}`);
    if (selectedId === id) setSelectedId(null);
    await loadDocuments();
  }

  async function handleReprocess(id) {
    await post(`/sources/${id}/reprocess`);
    await loadDocuments();
    trackProgress(id);
  }

  const selectedDoc = documents.find((d) => d.id === selectedId) ?? null;

  return (
    <div className="flex h-full flex-col bg-ink-950">
      <div className="flex items-center justify-between px-8 pb-6 pt-8">
        <h1 className="text-lg font-semibold text-slate-100">Data Source Corpus</h1>
        <div className="flex items-center gap-3">
          <div className="flex rounded-md border border-ink-700 p-0.5">
            <button
              onClick={() => setViewMode('table')}
              className={`rounded px-2.5 py-1 text-xs font-medium ${
                viewMode === 'table' ? 'bg-ink-700 text-slate-100' : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              List
            </button>
            <button
              onClick={() => setViewMode('grid')}
              className={`rounded px-2.5 py-1 text-xs font-medium ${
                viewMode === 'grid' ? 'bg-ink-700 text-slate-100' : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              Grid
            </button>
          </div>
          <button
            onClick={() => setModalOpen(true)}
            className="rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-500"
          >
            Upload New Document
          </button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 gap-6 px-8 pb-8">
        <div className="min-w-0 flex-1 overflow-y-auto">
          {viewMode === 'table' ? (
            <TableView
              documents={documents}
              departments={departments}
              roles={roles}
              progress={progress}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          ) : (
            <GridView
              documents={documents}
              departments={departments}
              roles={roles}
              progress={progress}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          )}
        </div>

        {selectedDoc && (
          <InspectorPanel
            doc={selectedDoc}
            departments={departments}
            roles={roles}
            live={progress[selectedDoc.id]}
            onClose={() => setSelectedId(null)}
            onDelete={() => handleDelete(selectedDoc.id)}
            onReprocess={() => handleReprocess(selectedDoc.id)}
          />
        )}
      </div>

      {modalOpen && (
        <UploadModal
          departments={departments}
          roles={roles}
          onClose={() => setModalOpen(false)}
          onUpload={handleUpload}
          error={error}
        />
      )}
    </div>
  );
}

function StatusBadge({ status, live }) {
  return (
    <div>
      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[status] ?? ''}`}>{status}</span>
      {status === 'PROCESSING' && (
        <div className="mt-1 h-1.5 w-24 overflow-hidden rounded-full bg-ink-800">
          <div className="h-full bg-brand-500 transition-all" style={{ width: `${live?.progress ?? 10}%` }} />
        </div>
      )}
    </div>
  );
}

function TableView({ documents, departments, roles, progress, selectedId, onSelect }) {
  return (
    <div className="overflow-hidden rounded-xl border border-ink-800 bg-ink-900">
      <table className="w-full text-sm">
        <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
          <tr className="border-b border-ink-800">
            <th className="px-4 py-3 font-medium">Title</th>
            <th className="px-4 py-3 font-medium">Uploaded</th>
            <th className="px-4 py-3 font-medium">Size</th>
            <th className="px-4 py-3 font-medium">Access</th>
            <th className="px-4 py-3 font-medium">Chunks</th>
            <th className="px-4 py-3 font-medium">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-ink-800">
          {documents.map((doc) => {
            const live = progress[doc.id];
            const status = live?.status ?? doc.status;
            const deptName = departments.find((d) => d.id === doc.departmentId)?.name ?? 'Org-wide';
            return (
              <tr
                key={doc.id}
                onClick={() => onSelect(doc.id)}
                className={`cursor-pointer ${selectedId === doc.id ? 'bg-ink-800' : 'hover:bg-ink-800/50'}`}
              >
                <td className="px-4 py-3 font-medium text-slate-200">{doc.title}</td>
                <td className="px-4 py-3 text-slate-500">{formatDate(doc.uploadedAt)}</td>
                <td className="px-4 py-3 text-slate-500">{formatBytes(doc.fileSize)}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    <span className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400">{deptName}</span>
                    <span className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400">
                      {levelLabel(doc.minimumRoleLevel, roles)}
                    </span>
                  </div>
                </td>
                <td className="px-4 py-3 text-slate-400">{doc.chunkCount}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={status} live={live} />
                </td>
              </tr>
            );
          })}
          {documents.length === 0 && (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-slate-600">
                No documents uploaded yet.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function GridView({ documents, departments, roles, progress, selectedId, onSelect }) {
  if (documents.length === 0) {
    return (
      <div className="rounded-xl border border-ink-800 bg-ink-900 px-4 py-10 text-center text-sm text-slate-600">
        No documents uploaded yet.
      </div>
    );
  }
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {documents.map((doc) => {
        const live = progress[doc.id];
        const status = live?.status ?? doc.status;
        const deptName = departments.find((d) => d.id === doc.departmentId)?.name ?? 'Org-wide';
        return (
          <button
            key={doc.id}
            onClick={() => onSelect(doc.id)}
            className={`flex flex-col items-start rounded-xl border p-4 text-left ${
              selectedId === doc.id ? 'border-brand-500 bg-ink-800' : 'border-ink-800 bg-ink-900 hover:bg-ink-800/50'
            }`}
          >
            <div className="mb-2 flex w-full items-start justify-between gap-2">
              <span className="truncate text-sm font-medium text-slate-200">{doc.title}</span>
            </div>
            <StatusBadge status={status} live={live} />
            <div className="mt-3 flex flex-wrap gap-1">
              <span className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400">{deptName}</span>
              <span className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400">
                {levelLabel(doc.minimumRoleLevel, roles)}
              </span>
            </div>
            <div className="mt-3 flex w-full items-center justify-between text-xs text-slate-500">
              <span>{doc.chunkCount} chunks</span>
              <span>{formatBytes(doc.fileSize)}</span>
            </div>
            <div className="mt-1 text-xs text-slate-600">{formatDate(doc.uploadedAt)}</div>
          </button>
        );
      })}
    </div>
  );
}

function InspectorPanel({ doc, departments, roles, live, onClose, onDelete, onReprocess }) {
  const status = live?.status ?? doc.status;
  const deptName = departments.find((d) => d.id === doc.departmentId)?.name ?? 'Org-wide';

  return (
    <div className="w-80 shrink-0 overflow-y-auto rounded-xl border border-ink-800 bg-ink-900 p-5">
      <div className="mb-4 flex items-start justify-between">
        <h2 className="pr-2 text-sm font-semibold text-slate-100">{doc.title}</h2>
        <button onClick={onClose} className="shrink-0 text-slate-500 hover:text-slate-200">
          ✕
        </button>
      </div>

      <div className="mb-5">
        <StatusBadge status={status} live={live} />
        {status === 'ERROR' && doc.errorMessage && (
          <p className="mt-2 text-xs text-red-400">{doc.errorMessage}</p>
        )}
      </div>

      <dl className="space-y-3 text-sm">
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Uploaded</dt>
          <dd className="text-slate-300">{formatDate(doc.uploadedAt)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Size</dt>
          <dd className="text-slate-300">{formatBytes(doc.fileSize)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Department</dt>
          <dd className="text-slate-300">{deptName}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Minimum visibility</dt>
          <dd className="text-slate-300">{levelLabel(doc.minimumRoleLevel, roles)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Indexed chunks</dt>
          <dd className="text-slate-300">{doc.chunkCount}</dd>
        </div>
      </dl>

      <div className="mt-6 flex gap-2 border-t border-ink-800 pt-4">
        <button
          onClick={onReprocess}
          className="flex-1 rounded-md border border-ink-700 px-3 py-2 text-xs font-medium text-brand-400 hover:bg-ink-800"
        >
          Re-process
        </button>
        <button
          onClick={onDelete}
          className="flex-1 rounded-md border border-red-900/50 px-3 py-2 text-xs font-medium text-red-400 hover:bg-red-950/20"
        >
          Delete
        </button>
      </div>
    </div>
  );
}

function UploadModal({ departments, roles, onClose, onUpload, error }) {
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [minimumRoleLevel, setMinimumRoleLevel] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const sortedRoles = [...roles].sort((a, b) => a.hierarchyLevel - b.hierarchyLevel);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!file) return;
    setSubmitting(true);
    await onUpload({ file, title, departmentId: departmentId || null, minimumRoleLevel: Number(minimumRoleLevel) });
    setSubmitting(false);
  }

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-md rounded-xl border border-ink-800 bg-ink-900 p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-slate-100">Upload Document</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragging(false);
              const f = e.dataTransfer.files?.[0];
              if (f) setFile(f);
            }}
            className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-6 text-center text-sm ${
              dragging ? 'border-brand-500 bg-brand-500/10' : 'border-ink-700 text-slate-500'
            }`}
          >
            {file ? (
              <span className="font-medium text-slate-200">{file.name}</span>
            ) : (
              <>
                <span>Drag & drop a file here, or</span>
                <label className="mt-2 cursor-pointer text-brand-400 hover:underline">
                  browse
                  <input
                    type="file"
                    className="hidden"
                    onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                  />
                </label>
              </>
            )}
          </div>

          <div>
            <label className="mb-1 block text-sm text-slate-400">Title (optional)</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={file?.name ?? 'Document title'}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm text-slate-400">Department</label>
            <select
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">Org-wide (every department)</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm text-slate-400">Minimum visibility</label>
            <select
              value={minimumRoleLevel}
              onChange={(e) => setMinimumRoleLevel(e.target.value)}
              className="w-full rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value={0}>Everyone</option>
              {sortedRoles.map((r) => (
                <option key={r.id} value={r.hierarchyLevel}>{r.name} and above</option>
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
              disabled={!file || submitting}
              className="rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-500 disabled:opacity-50"
            >
              {submitting ? 'Uploading…' : 'Upload'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
