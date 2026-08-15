const BASE = '/api/v1';

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function handle(res) {
  if (res.status === 204) return null;
  const isJson = res.headers.get('content-type')?.includes('application/json');
  const body = isJson ? await res.json().catch(() => null) : null;
  if (!res.ok) {
    throw new ApiError(body?.error || res.statusText, res.status);
  }
  return body;
}

export function get(path) {
  return fetch(BASE + path, { credentials: 'include' }).then(handle);
}

export function post(path, data) {
  return fetch(BASE + path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: data !== undefined ? JSON.stringify(data) : undefined,
  }).then(handle);
}

export function put(path, data) {
  return fetch(BASE + path, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(handle);
}

export function del(path) {
  return fetch(BASE + path, { method: 'DELETE', credentials: 'include' }).then(handle);
}

export function upload(path, formData) {
  return fetch(BASE + path, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  }).then(handle);
}

/**
 * Consumes a text/event-stream POST response by hand (EventSource can't send POST bodies).
 * Parses "event: name\ndata: payload\n\n" frames and invokes handlers[name] with the payload.
 */
export async function streamPost(path, data, handlers) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!res.ok || !res.body) {
    const body = await res.json().catch(() => null);
    throw new ApiError(body?.error || res.statusText, res.status);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let sepIndex;
    while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, sepIndex);
      buffer = buffer.slice(sepIndex + 2);
      let eventName = 'message';
      const dataLines = [];
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          // Spring's SseEmitter writes "data:" directly followed by the raw content, with no
          // padding space of its own — so the entire remainder is real content and must be taken
          // verbatim. Streamed tokens carry meaningful leading/trailing whitespace (e.g. " the");
          // stripping anything here (even one "conventional" space) eats that and runs words together.
          dataLines.push(line.slice(5));
        }
      }
      const payload = dataLines.join('\n');
      const handler = handlers[eventName];
      if (handler) {
        try {
          handler(JSON.parse(payload));
        } catch {
          handler(payload);
        }
      }
    }
  }
}

export { ApiError };
