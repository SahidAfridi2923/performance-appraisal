const COLORS = ['#2563eb', '#7c3aed', '#0891b2', '#16a34a', '#ea580c', '#db2777', '#4f46e5', '#0d9488'];

export const initials = (name = '') =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');

export default function Avatar({ name, large }) {
  const hash = [...(name || '')].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7);
  return (
    <span className={`avatar ${large ? 'lg' : ''}`} style={{ background: COLORS[hash % COLORS.length] }} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

export function Person({ name, meta }) {
  return (
    <div className="person">
      <Avatar name={name} />
      <div>
        <div className="name">{name}</div>
        {meta && <div className="meta">{meta}</div>}
      </div>
    </div>
  );
}
