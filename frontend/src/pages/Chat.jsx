import { useEffect, useRef, useState } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { del, get, put, streamPost } from '../api/client.js';

function formatTime(iso) {
  try {
    return new Date(iso).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

function groupThreadsByDate(threads) {
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfYesterday = new Date(startOfToday);
  startOfYesterday.setDate(startOfYesterday.getDate() - 1);
  const startOfWeek = new Date(startOfToday);
  startOfWeek.setDate(startOfWeek.getDate() - 7);

  const groups = [
    { label: 'Today', items: [] },
    { label: 'Yesterday', items: [] },
    { label: 'Previous 7 Days', items: [] },
    { label: 'Older', items: [] },
  ];
  for (const t of threads) {
    const updated = new Date(t.updatedAt);
    if (updated >= startOfToday) groups[0].items.push(t);
    else if (updated >= startOfYesterday) groups[1].items.push(t);
    else if (updated >= startOfWeek) groups[2].items.push(t);
    else groups[3].items.push(t);
  }
  return groups.filter((g) => g.items.length > 0);
}

export function Chat() {
  const [threads, setThreads] = useState([]);
  const [activeThreadId, setActiveThreadId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [pendingAssistant, setPendingAssistant] = useState('');
  const [pendingSources, setPendingSources] = useState([]);
  const scrollRef = useRef(null);

  async function loadThreads() {
    const list = await get('/threads');
    setThreads(list);
    return list;
  }

  useEffect(() => {
    loadThreads();
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, pendingAssistant]);

  async function selectThread(id) {
    setActiveThreadId(id);
    const msgs = await get(`/threads/${id}/messages`);
    setMessages(msgs);
  }

  function startNewThread() {
    setActiveThreadId(null);
    setMessages([]);
  }

  async function renameThread(id, title) {
    await put(`/threads/${id}`, { title });
    await loadThreads();
  }

  async function deleteThread(id) {
    await del(`/threads/${id}`);
    if (activeThreadId === id) {
      startNewThread();
    }
    await loadThreads();
  }

  async function handleSend(e) {
    e.preventDefault();
    const query = draft.trim();
    if (!query || streaming) return;

    setDraft('');
    setStreaming(true);
    setPendingAssistant('');
    setPendingSources([]);

    const optimisticQuery = { id: `pending-${Date.now()}`, query, response: null, sources: [], createdAt: new Date().toISOString() };
    setMessages((prev) => [...prev, optimisticQuery]);

    let resolvedThreadId = activeThreadId;
    let fullResponse = '';
    let sources = [];

    try {
      await streamPost('/chat', { threadId: activeThreadId, query }, {
        sources: (payload) => {
          sources = Array.isArray(payload) ? payload : [];
          setPendingSources(sources);
        },
        token: (payload) => {
          const text = typeof payload === 'string' ? payload : String(payload ?? '');
          fullResponse += text;
          setPendingAssistant((prev) => prev + text);
        },
        done: async () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === optimisticQuery.id
                ? { ...m, response: fullResponse, sources }
                : m
            )
          );
          setPendingAssistant('');
          setPendingSources([]);
          setStreaming(false);
          const list = await loadThreads();
          if (!resolvedThreadId && list.length > 0) {
            resolvedThreadId = list[0].id;
            setActiveThreadId(resolvedThreadId);
          }
        },
      });
    } catch (err) {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === optimisticQuery.id
            ? { ...m, response: `_Error: ${err.message || 'request failed'}_`, sources: [] }
            : m
        )
      );
      setPendingAssistant('');
      setStreaming(false);
    }
  }

  const activeThread = threads.find((t) => t.id === activeThreadId) ?? null;
  const referencedDocs = [...new Set(messages.flatMap((m) => (m.sources || []).map((s) => s.docTitle)))];

  return (
    <div className="flex h-full">
      <aside className="flex w-64 shrink-0 flex-col border-r border-ink-800 bg-ink-900">
        <div className="flex items-center justify-between px-4 pt-4 pb-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">Threads</span>
          <button
            onClick={startNewThread}
            className="rounded-md bg-brand-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-brand-500"
          >
            + New
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-2 pb-2">
          {groupThreadsByDate(threads).map((group) => (
            <div key={group.label} className="mb-2">
              <div className="px-3 pb-1 pt-3 text-xs font-medium uppercase tracking-wide text-slate-600">
                {group.label}
              </div>
              {group.items.map((t) => (
                <ThreadRow
                  key={t.id}
                  thread={t}
                  active={activeThreadId === t.id}
                  onSelect={() => selectThread(t.id)}
                  onRename={(title) => renameThread(t.id, title)}
                  onDelete={() => deleteThread(t.id)}
                />
              ))}
            </div>
          ))}
          {threads.length === 0 && (
            <p className="px-3 py-4 text-sm text-slate-600">No conversations yet.</p>
          )}
        </div>
      </aside>

      <section className="flex flex-1 flex-col bg-ink-950">
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-6 py-6">
          {messages.length === 0 && !pendingAssistant && (
            <div className="mx-auto max-w-lg pt-24 text-center text-slate-500">
              Ask a question about your organization&rsquo;s knowledge base.
            </div>
          )}
          <div className="mx-auto flex max-w-3xl flex-col gap-6">
            {messages.map((m) => (
              <div key={m.id} className="flex flex-col gap-3">
                <div className="flex justify-end">
                  <div className="max-w-xl break-words rounded-2xl rounded-br-sm bg-brand-600 px-4 py-2 text-sm text-white">
                    {m.query}
                  </div>
                </div>
                {m.response !== null && (
                  <div className="flex justify-start">
                    <div className="max-w-xl break-words rounded-2xl rounded-bl-sm border border-ink-800 bg-ink-900 px-4 py-3 text-sm shadow-sm">
                      <div className="prose prose-sm max-w-none">
                        <Markdown remarkPlugins={[remarkGfm]}>{m.response}</Markdown>
                      </div>
                      <SourcePills sources={m.sources} />
                    </div>
                  </div>
                )}
              </div>
            ))}

            {streaming && (
              <div className="flex justify-start">
                <div className="max-w-xl break-words rounded-2xl rounded-bl-sm border border-ink-800 bg-ink-900 px-4 py-3 text-sm shadow-sm">
                  <div className="prose prose-sm max-w-none">
                    {pendingAssistant ? (
                      <Markdown remarkPlugins={[remarkGfm]}>{pendingAssistant}</Markdown>
                    ) : (
                      <span className="text-slate-500">Thinking</span>
                    )}
                    <span className="blinking-cursor text-brand-400">▍</span>
                  </div>
                  <SourcePills sources={pendingSources} />
                </div>
              </div>
            )}
          </div>
        </div>

        <form onSubmit={handleSend} className="border-t border-ink-800 bg-ink-900 p-4">
          <div className="mx-auto flex max-w-3xl items-end gap-2">
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend(e);
                }
              }}
              rows={1}
              placeholder="Ask the concierge..."
              className="max-h-40 flex-1 resize-none rounded-lg border border-ink-700 bg-ink-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <button
              type="submit"
              disabled={streaming || !draft.trim()}
              className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50"
            >
              Send
            </button>
          </div>
        </form>
      </section>

      {activeThread && (
        <aside className="w-72 shrink-0 overflow-y-auto border-l border-ink-800 bg-ink-900 p-5">
          <h2 className="mb-4 break-words text-sm font-semibold text-slate-100">{activeThread.title}</h2>
          <dl className="space-y-4 text-sm">
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Started</dt>
              <dd className="text-slate-300">{formatTime(activeThread.createdAt)}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Last activity</dt>
              <dd className="text-slate-300">{formatTime(activeThread.updatedAt)}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Messages</dt>
              <dd className="text-slate-300">{messages.length}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Documents referenced</dt>
              <dd className="mt-1 flex flex-wrap gap-1">
                {referencedDocs.length > 0 ? (
                  referencedDocs.map((d) => (
                    <span key={d} className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400">
                      {d}
                    </span>
                  ))
                ) : (
                  <span className="text-slate-600">None yet</span>
                )}
              </dd>
            </div>
          </dl>
        </aside>
      )}
    </div>
  );
}

function SourcePills({ sources }) {
  if (!sources || sources.length === 0) return null;
  return (
    <div className="mt-2 flex flex-wrap gap-1.5">
      {sources.map((s, i) => (
        <span
          key={s.chunkId ?? i}
          title={s.docTitle}
          className="rounded-full bg-ink-800 px-2 py-0.5 text-xs text-slate-400"
        >
          {s.docTitle}
        </span>
      ))}
    </div>
  );
}

function ThreadRow({ thread, active, onSelect, onRename, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [draftTitle, setDraftTitle] = useState(thread.title);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return undefined;
    function handleClickOutside(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  function startRename() {
    setDraftTitle(thread.title);
    setRenaming(true);
    setMenuOpen(false);
  }

  async function commitRename() {
    const title = draftTitle.trim();
    setRenaming(false);
    if (title && title !== thread.title) {
      await onRename(title);
    }
  }

  function handleDelete() {
    setMenuOpen(false);
    if (confirm(`Delete "${thread.title}"? This can't be undone.`)) {
      onDelete();
    }
  }

  if (renaming) {
    return (
      <div className="mb-1 px-3 py-2">
        <input
          autoFocus
          value={draftTitle}
          onChange={(e) => setDraftTitle(e.target.value)}
          onBlur={commitRename}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              commitRename();
            } else if (e.key === 'Escape') {
              setRenaming(false);
            }
          }}
          className="w-full rounded-md border border-brand-500 bg-ink-950 px-2 py-1 text-sm text-slate-100 focus:outline-none"
        />
      </div>
    );
  }

  return (
    <div
      className={`group relative mb-1 flex items-center rounded-md text-sm ${
        active ? 'bg-ink-800 text-brand-300' : 'text-slate-400 hover:bg-ink-800/60'
      }`}
    >
      <button onClick={onSelect} className="min-w-0 flex-1 truncate px-3 py-2 text-left">
        <div className="truncate pr-5">{thread.title}</div>
        <div className="text-xs text-slate-600">{formatTime(thread.updatedAt)}</div>
      </button>
      <button
        onClick={() => setMenuOpen((v) => !v)}
        className={`absolute right-1 top-1/2 -translate-y-1/2 rounded-md px-1.5 py-1 text-slate-500 hover:bg-ink-700 hover:text-slate-200 ${
          menuOpen ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
        }`}
        aria-label="Thread options"
      >
        &#8942;
      </button>
      {menuOpen && (
        <div
          ref={menuRef}
          className="absolute right-1 top-9 z-10 w-32 overflow-hidden rounded-md border border-ink-700 bg-ink-900 py-1 shadow-lg"
        >
          <button
            onClick={startRename}
            className="block w-full px-3 py-1.5 text-left text-xs text-slate-300 hover:bg-ink-800"
          >
            Rename
          </button>
          <button
            onClick={handleDelete}
            className="block w-full px-3 py-1.5 text-left text-xs text-red-400 hover:bg-ink-800"
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
