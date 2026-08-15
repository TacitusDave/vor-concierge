import { useEffect, useState } from 'react';
import { get } from '../api/client.js';

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return 'Unlimited';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let value = bytes;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}

function StatCard({ label, value }) {
  return (
    <div className="rounded-xl border border-ink-800 bg-ink-900 p-4">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-brand-400">{value}</div>
    </div>
  );
}

export function Dashboard() {
  const [overview, setOverview] = useState(null);

  useEffect(() => {
    get('/organization/overview').then(setOverview);
  }, []);

  if (!overview) {
    return <div className="flex h-full items-center justify-center text-slate-500">Loading…</div>;
  }

  const usersLimit = overview.maxUsers ?? 'Unlimited';
  const docsLimit = overview.maxDocuments ?? 'Unlimited';

  return (
    <div className="h-full overflow-y-auto bg-ink-950 p-8">
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-slate-100">{overview.organizationName}</h1>
        <p className="mt-1 text-sm text-slate-500">
          {overview.planName} plan &middot; {overview.subscriptionStatus}
        </p>
      </div>

      <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Members" value={`${overview.memberCount} / ${usersLimit}`} />
        <StatCard label="Departments" value={overview.departmentCount} />
        <StatCard label="Documents" value={`${overview.documentCount} / ${docsLimit}`} />
        <StatCard label="Chunks Indexed" value={overview.chunkCount} />
        <StatCard label="Tokens Today" value={overview.tokensToday} />
        <StatCard label="Storage Cap" value={formatBytes(overview.maxStorageBytes)} />
      </div>

      <div className="rounded-xl border border-ink-800 bg-ink-900 p-5 text-sm text-slate-400">
        Use <span className="text-slate-200">Organization</span> to set up departments and roles, and{' '}
        <span className="text-slate-200">Members</span> to invite staff. Everyone in your organization uses{' '}
        <span className="text-slate-200">Chat</span> to ask questions against whatever your role and department
        give you access to.
      </div>
    </div>
  );
}
