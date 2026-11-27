// HumAppraisal wordmark in the HumHealth colours (teal → blue → coral → navy).
// The "i" has a leaf instead of its dot: the word is drawn as "HumAppra" + an i-stem + "sal",
// measured from Nunito 700 at 46px ("HumAppra" = 234.9 wide, "i" advance = 11.7, stem x 2-9, height 23),
// so it does not depend on the font having a dotless-i glyph. The gradient spans the whole word
// (userSpaceOnUse), so it runs continuously across the three pieces.
const FONT = "'Nunito', 'Plus Jakarta Sans', system-ui, sans-serif";
const X0 = 2; // text start
const BASE = 54; // baseline
const PRE = 234.9; // width of "HumAppra"
const I_ADV = 11.73; // advance width of "i"
const GAP = 3; // extra space between "a" and the leaf-i so the i reads as its own letter
const STEM_X = X0 + PRE + 2 + GAP; // left edge of the i stem
const STEM_W = 8;
const STEM_TOP = BASE - 23;

function Leaf({ x, y, scale = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      <path d="M1 21 C 1 9, 10 1, 23 0 C 23 12, 15 21, 1 21 Z" fill="url(#hum-leaf)" />
      <path d="M2.5 19.5 C 8 14, 14 8.5, 20.5 3" fill="none" stroke="#a9d8b4" strokeWidth={1.6 / scale} strokeLinecap="round" />
    </g>
  );
}

export default function Logo({ height = 30, title = 'HumAppraisal' }) {
  return (
    <svg className="logo" height={height} viewBox="0 6 317 56" role="img" aria-label={title}>
      <defs>
        <linearGradient id="hum-word" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="315" y2="0">
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
      <g fontFamily={FONT} fontWeight="700" fontSize="46" fill="url(#hum-word)">
        <text x={X0} y={BASE}>
          HumAppra
        </text>
        <rect x={STEM_X} y={STEM_TOP} width={STEM_W} height={BASE - STEM_TOP + 0.6} rx={STEM_W / 2} />
        <text x={X0 + PRE + I_ADV + GAP} y={BASE}>
          sal
        </text>
      </g>
      {/* the leaf is the dot of the i */}
      <Leaf x={STEM_X + 0.6} y={STEM_TOP - 17.5} scale={0.68} />
    </svg>
  );
}
