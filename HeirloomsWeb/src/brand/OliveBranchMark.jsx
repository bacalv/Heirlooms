export function OliveBranchMark({
  width = 130,
  state = 'bloomed',
  className,
  style,
  decorative = true,
  label,
}) {
  const fruitFill = state === 'bloomed' ? 'var(--hl-bloom)' : 'var(--hl-forest)'
  const aria = decorative
    ? { 'aria-hidden': true }
    : { role: 'img', 'aria-label': label || 'Heirlooms' }

  return (
    <svg
      viewBox="0 0 140 200"
      width={width}
      height={Math.round(width * (200 / 140))}
      className={className}
      style={style}
      {...aria}
    >
      <path d="M 70,140 C 73,118 67,88 70,58" stroke="var(--hl-forest)" strokeWidth="1.7" fill="none" strokeLinecap="round" />
      <ellipse cx="56" cy="124" rx="9.5" ry="2.4" fill="var(--hl-forest)" transform="rotate(18 67 122)" />
      <ellipse cx="84" cy="124" rx="9.5" ry="2.4" fill="var(--hl-forest)" transform="rotate(-18 73 122)" />
      <ellipse cx="55" cy="103" rx="8.5" ry="2.2" fill="var(--hl-forest)" transform="rotate(22 64 101)" />
      <ellipse cx="85" cy="103" rx="8.5" ry="2.2" fill="var(--hl-forest)" transform="rotate(-22 76 101)" />
      <ellipse cx="58" cy="82"  rx="7"   ry="1.9" fill="var(--hl-forest)" transform="rotate(26 65 80)"  />
      <ellipse cx="82" cy="82"  rx="7"   ry="1.9" fill="var(--hl-forest)" transform="rotate(-26 75 80)" />
      <ellipse cx="70" cy="55"  rx="3.5" ry="5.5" fill={fruitFill} />
    </svg>
  )
}
