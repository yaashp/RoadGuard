// Reports waiting on this device. When an upload fails because of the network, the report (and its photo) is kept in IndexedDB
// and sent again automatically once the connection is back. Each report carries a random key the server uses to ignore repeats,
// so a retry can never create a second report.
import { api, ApiError } from './api.js';

const DB_NAME = 'roadguard', STORE = 'pending';
let dbPromise = null;

export const queueSupported = () => typeof indexedDB !== 'undefined';

function open() {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(STORE, { keyPath: 'id' });
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }
  return dbPromise;
}

async function tx(mode, fn) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, mode);
    const r = fn(t.objectStore(STORE));
    t.oncomplete = () => resolve(r && 'result' in r ? r.result : undefined);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  });
}

const changed = () => window.dispatchEvent(new CustomEvent('rg-queue'));

export async function queuedReports() { return queueSupported() ? ((await tx('readonly', (s) => s.getAll())) || []).sort((a, b) => a.createdAt - b.createdAt) : []; }
export async function saveQueued(item) { if (!queueSupported()) throw new Error('This browser cannot keep reports offline.'); await tx('readwrite', (s) => s.put(item)); changed(); }
export async function removeQueued(id) { if (queueSupported()) { await tx('readwrite', (s) => s.delete(id)); changed(); } }
export async function updateQueued(id, patch) {
  const all = await queuedReports();
  const cur = all.find((x) => x.id === id);
  if (cur) await saveQueued({ ...cur, ...patch });
}

export function newRequestId() {
  const bytes = new Uint8Array(16);
  (globalThis.crypto || window.crypto).getRandomValues(bytes);
  return 'rg' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

function photoName(photo) {
  if (photo.name) return photo.name;
  return 'photo.' + (photo.type === 'image/png' ? 'png' : photo.type === 'image/webp' ? 'webp' : 'jpg');
}

export function buildForm(item) {
  const f = new FormData();
  Object.entries(item.fields).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') f.append(k, v); });
  f.append('clientRequestId', item.id);
  if (item.allowDuplicate) f.append('allowDuplicate', 'true');
  if (item.photo) f.append('image', item.photo, photoName(item.photo));
  return f;
}

/** Sends one report. Resolves with the created (or already existing) complaint; throws ApiError otherwise. */
export function sendReport(item) { return api.upload('/api/complaints', buildForm(item)); }

let flushing = false;
/** Tries to upload everything waiting on this device. Stops at the first network problem. Returns the complaints sent. */
export async function flushQueue() {
  if (flushing || !queueSupported()) return [];
  flushing = true;
  const sent = [];
  try {
    for (const item of await queuedReports()) {
      if (item.state === 'attention') continue;           // needs a decision from the user first
      await updateQueued(item.id, { state: 'uploading', message: '' });
      try {
        const c = await sendReport(item);
        await removeQueued(item.id);
        sent.push(c);
      } catch (e) {
        if (!(e instanceof ApiError) || e.status === 0 || e.status >= 500 || e.status === 401 || e.status === 429) {
          await updateQueued(item.id, { state: 'saved', message: e.status === 401 ? 'Log in again to send this report.' : 'Still waiting for a connection.' });
          break;
        }
        await updateQueued(item.id, { state: 'attention', message: e.message, code: e.code });
      }
    }
  } finally { flushing = false; changed(); }
  return sent;
}
