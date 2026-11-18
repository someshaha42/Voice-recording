export function formatTimerMs(ms: number): string {
  const totalTenths = Math.max(0, Math.floor(ms / 100));
  const tenths = totalTenths % 10;
  const totalSec = Math.floor(totalTenths / 10);
  const sec = totalSec % 60;
  const min = Math.floor(totalSec / 60) % 60;
  const hr = Math.floor(totalSec / 3600);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(hr)}:${pad(min)}:${pad(sec)}.${tenths}`;
}

export function formatShortDuration(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const sec = totalSec % 60;
  const min = Math.floor(totalSec / 60);
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

export function formatFreqHz(hz: number): string {
  if (hz >= 1000) {
    return `${(hz / 1000).toFixed(2)} kHz`;
  }
  return `${Math.round(hz)} Hz`;
}

export function formatDb(db: number): string {
  const sign = db >= 0 ? '+' : '';
  return `${sign}${db.toFixed(1)} dB`;
}
