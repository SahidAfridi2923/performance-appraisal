export const ROLE_LABEL = { SELF: 'Self', PEER: 'Peer', TL: 'TL', MANAGER: 'Manager' };
export const ROLE_ORDER = ['SELF', 'PEER', 'TL', 'MANAGER'];

export const STATUS_LABEL = {
  NOT_STARTED: 'Not started',
  SELECT_LEVEL: 'Select level',
  INCOMPLETE: 'Incomplete: TL/Manager review',
  CHECK_ANSWERS: 'Check answers',
  SCORED: 'Scored',
};

export const fmt = (v, d = 2) => (v === null || v === undefined ? '–' : Number(v).toFixed(d).replace(/\.?0+$/, ''));
export const pct = (v) => (v === null || v === undefined ? '–' : `${Math.round(v * 1000) / 10}%`);
export const date = (v) => (v ? new Date(v).toLocaleDateString() : '–');

export function ratingClass(rating) {
  if (!rating) return '';
  const r = rating.toLowerCase();
  if (r.includes('outstanding')) return 'r5';
  if (r.includes('exceeds')) return 'r4';
  if (r.includes('meets')) return 'r3';
  if (r.includes('needs')) return 'r2';
  return 'r1';
}
