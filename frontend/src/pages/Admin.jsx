import { useEffect, useRef, useState } from 'react';
import { get, post, put } from '../api/client.js';

function useDebouncedCallback(callback, delay) {
  const timeoutRef = useRef(null);
  return (...args) => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => callback(...args), delay);
  };
}

function formatUptime(ms) {
  if (!ms && ms !== 0) return '—';
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) return `${hours} hr ${minutes} min`;
  return `${minutes} min`;
}

function StatCard({ label, value }) {
  return (
    <div className="rounded-xl border border-ink-800 bg-ink-900 p-4">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-brand-400">{value}</div>
    </div>
  );
}

function Slider({ label, value, min, max, step, onChange }) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-sm">
        <span className="font-medium text-slate-300">{label}</span>
        <span className="font-mono text-slate-500">{Number(value).toFixed(2)}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-brand-500"
      />
    </div>
  );
}

export function Admin() {
  const [health, setHealth] = useState(null);
  const [config, setConfig] = useState({ temperature: 0.7, top_p: 0.9, frequency_penalty: 0.0 });
  const [modelInfo, setModelInfo] = useState({ llmModel: '', embeddingModel: '' });
  const [killing, setKilling] = useState(false);
  const [killedAt, setKilledAt] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const h = await get('/admin/health');
        if (!cancelled) setHealth(h);
      } catch {
        /* transient */
      }
    }
    poll();
    const interval = setInterval(poll, 5000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    get('/admin/config').then((c) => {
      setConfig({
        temperature: parseFloat(c.temperature ?? '0.7'),
        top_p: parseFloat(c.top_p ?? '0.9'),
        frequency_penalty: parseFloat(c.frequency_penalty ?? '0.0'),
      });
      setModelInfo({ llmModel: c.llmModel ?? '', embeddingModel: c.embeddingModel ?? '' });
    });
  }, []);

  const pushConfig = useDebouncedCallback((next) => {
    put('/admin/config', {
      settings: {
        temperature: String(next.temperature),
        top_p: String(next.top_p),
        frequency_penalty: String(next.frequency_penalty),
      },
    });
  }, 400);

  function updateConfig(key, value) {
    setConfig((prev) => {
      const next = { ...prev, [key]: value };
      pushConfig(next);
      return next;
    });
  }

  async function handleKill() {
    if (!confirm('This immediately terminates every active chat stream. Continue?')) return;
    setKilling(true);
    try {
      await post('/admin/kill');
      setKilledAt(new Date());
    } finally {
      setKilling(false);
    }
  }

  return (
    <div className="h-full overflow-y-auto bg-ink-950 p-8">
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-slate-100">System Governance</h1>
      </div>

      <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Uptime" value={formatUptime(health?.uptimeMs)} />
        <StatCard label="Heap Used" value={health ? `${health.heapUsedMb} MB` : '—'} />
        <StatCard label="Load Avg" value={health?.loadAvg ?? '—'} />
        <StatCard label="Active Users" value={health?.activeUsers ?? '—'} />
        <StatCard label="Documents" value={health?.documents ?? '—'} />
        <StatCard label="Chunks" value={health?.chunks ?? '—'} />
        <StatCard label="Tokens Today" value={health?.tokensToday ?? '—'} />
        <StatCard label="Active Streams" value={health?.activeSseConnections ?? '—'} />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-ink-800 bg-ink-900 p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-200">LLM Configuration</h2>
            <span
              className={`flex items-center gap-1.5 text-xs ${
                health?.ollamaHealthy ? 'text-brand-400' : 'text-red-400'
              }`}
            >
              <span
                className={`h-1.5 w-1.5 rounded-full ${
                  health?.ollamaHealthy ? 'bg-brand-400' : 'bg-red-400'
                }`}
              />
              {health ? (health.ollamaHealthy ? 'Ollama healthy' : 'Ollama unreachable') : ''}
            </span>
          </div>
          <div className="space-y-5">
            <Slider
              label="Temperature"
              value={config.temperature}
              min={0}
              max={1}
              step={0.05}
              onChange={(v) => updateConfig('temperature', v)}
            />
            <Slider
              label="Top-P"
              value={config.top_p}
              min={0}
              max={1}
              step={0.05}
              onChange={(v) => updateConfig('top_p', v)}
            />
            <Slider
              label="Frequency Penalty"
              value={config.frequency_penalty}
              min={0}
              max={2}
              step={0.1}
              onChange={(v) => updateConfig('frequency_penalty', v)}
            />
          </div>
          <p className="mt-4 text-xs text-slate-600">
            Model: {modelInfo.llmModel || '—'} · Embeddings: {modelInfo.embeddingModel || '—'}
          </p>
        </div>

        <div className="rounded-xl border border-red-900/50 bg-red-950/20 p-5">
          <h2 className="mb-2 text-sm font-semibold text-red-400">Emergency Kill-Switch</h2>
          <p className="mb-4 text-sm text-red-300/80">
            Immediately terminates every active AI streaming connection and clears internal caches.
          </p>
          <button
            onClick={handleKill}
            disabled={killing}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-500 disabled:opacity-50"
          >
            {killing ? 'Terminating…' : 'Activate Kill-Switch'}
          </button>
          {killedAt && (
            <p className="mt-3 text-sm text-red-300/80">
              Kill-switch activated at {killedAt.toLocaleTimeString()}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
