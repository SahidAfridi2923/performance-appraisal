// HumAppraisal wordmark: HumHealth colour gradient (teal → blue → coral → navy) with the leaf on the last letter.
export default function Logo({ height = 30, title = 'HumAppraisal' }) {
  // fonts load async; the SVG re-renders with Nunito once it arrives (fallback keeps the same layout box)
  return (
    <svg className="logo" height={height} viewBox="0 0 332 62" role="img" aria-label={title}>
      <defs>
        <linearGradient id="hum-word" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#6cbab8" />
          <stop offset="0.26" stopColor="#4fa7b1" />
          <stop offset="0.46" stopColor="#4f9fc0" />
          <stop offset="0.6" stopColor="#8a8aa5" />
          <stop offset="0.72" stopColor="#e8705a" />
          <stop offset="0.84" stopColor="#9b7a84" />
          <stop offset="1" stopColor="#2a537c" />
        </linearGradient>
        <linearGradient id="hum-leaf" x1="0" y1="1" x2="1" y2="0">
          <stop offset="0" stopColor="#3f8a58" />
          <stop offset="1" stopColor="#6fb381" />
        </linearGradient>
      </defs>
      {/* natural Nunito 700 width at 46px is ~309 units; the final "l" stem sits at x ≈ 298-306, its top at y ≈ 21 */}
      <text x="2" y="54" fontFamily="'Nunito', 'Plus Jakarta Sans', system-ui, sans-serif" fontWeight="700" fontSize="46" fill="url(#hum-word)">
        HumAppraisal
      </text>
      {/* leaf growing from the top of the final "l" */}
      <g transform="translate(302 0)">
        <path d="M1 21 C 1 9, 10 1, 23 0 C 23 12, 15 21, 1 21 Z" fill="url(#hum-leaf)" />
        <path d="M2.5 19.5 C 8 14, 14 8.5, 20.5 3" fill="none" stroke="#a9d8b4" strokeWidth="1.6" strokeLinecap="round" />
      </g>
    </svg>
  );
}

/** Square mark for small spaces: an "H" in the brand gradient with the leaf. */
export function LogoMark({ size = 32 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient id="hum-mark" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#5fb3b2" />
          <stop offset="0.5" stopColor="#4f9fc0" />
          <stop offset="1" stopColor="#2a537c" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill="#ffffff" />
      <text x="32" y="47" textAnchor="middle" fontFamily="'Nunito', system-ui, sans-serif" fontWeight="900" fontSize="40" fill="url(#hum-mark)">
        H
      </text>
      <path d="M40 18 C 40 10, 46 5, 55 4 C 55 12, 50 18, 40 18 Z" fill="#509868" />
    </svg>
  );
}
