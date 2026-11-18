import { AudioConfig, RecordingItem } from '../types/audio';

const DB_NAME = 'voxforge_recordings_db';
const DB_VERSION = 1;
const STORE_NAME = 'recordings';

interface StoredRecordingRecord extends RecordingItem {
  blob: Blob;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('Failed to open IndexedDB'));
  });
}

export function formatDefaultRecordingFileName(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(
    now.getHours()
  )}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  return `Voiceover_${stamp}.wav`;
}

export async function loadAllRecordings(): Promise<RecordingItem[]> {
  try {
    const db = await openDb();
    return await new Promise<RecordingItem[]>((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.getAll();
      req.onsuccess = () => {
        const records = (req.result as StoredRecordingRecord[]) || [];
        const items: RecordingItem[] = records
          .map(({ blob: _blob, ...meta }) => meta)
          .sort((a, b) => b.createdAtMs - a.createdAtMs);
        resolve(items);
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}

export async function saveRecordingBlob(
  blob: Blob,
  durationMs: number,
  sizeBytes: number,
  customName?: string
): Promise<RecordingItem> {
  const createdAtMs = Date.now();
  const id = `rec_${createdAtMs}_${Math.random().toString(36).slice(2, 7)}`;
  const fileName = customName ?? formatDefaultRecordingFileName(new Date(createdAtMs));
  const record: StoredRecordingRecord = {
    id,
    fileName,
    createdAtMs,
    durationMs,
    sizeBytes,
    sampleRateHz: AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
    bitsPerSample: AudioConfig.OUTPUT_BITS_PER_SAMPLE,
    channels: AudioConfig.OUTPUT_CHANNELS,
    wasRecovered: false,
    blob,
  };

  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put(record);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });

  const { blob: _b, ...meta } = record;
  return meta;
}

export async function getRecordingBlob(id: string): Promise<Blob | null> {
  const db = await openDb();
  return new Promise<Blob | null>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const req = tx.objectStore(STORE_NAME).get(id);
    req.onsuccess = () => {
      const rec = req.result as StoredRecordingRecord | undefined;
      resolve(rec?.blob ?? null);
    };
    req.onerror = () => reject(req.error);
  });
}

export async function renameRecordingInDb(id: string, newBaseName: string): Promise<RecordingItem> {
  const sanitized = newBaseName
    .trim()
    .replace(/[^a-zA-Z0-9._ -]/g, '_')
    .replace(/\.(wav|WAV)$/i, '')
    .trim();

  if (!sanitized) {
    throw new Error('Recording name cannot be empty.');
  }

  const db = await openDb();
  const record = await new Promise<StoredRecordingRecord | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const req = tx.objectStore(STORE_NAME).get(id);
    req.onsuccess = () => resolve(req.result as StoredRecordingRecord | undefined);
    req.onerror = () => reject(req.error);
  });

  if (!record) {
    throw new Error('Source recording no longer exists.');
  }

  const updated: StoredRecordingRecord = {
    ...record,
    fileName: `${sanitized}.wav`,
  };

  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put(updated);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });

  const { blob: _b, ...meta } = updated;
  return meta;
}

export async function deleteRecordingFromDb(id: string): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
