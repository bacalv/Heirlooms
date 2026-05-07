export function OliveBranchIcon({ width = 22, className, style }) {
  return (
    <svg
      viewBox="0 0 30 30"
      width={width}
      height={width}
      className={className}
      style={style}
      aria-hidden="true"
    >
      <path d="M 15,28 C 16,22 14,15 15,9" stroke="var(--hl-forest)" strokeWidth="0.9" fill="none" strokeLinecap="round" />
      <ellipse cx="9"  cy="22" rx="3.4" ry="0.95" fill="var(--hl-forest)" transform="rotate(18 13 21.5)"  />
      <ellipse cx="21" cy="22" rx="3.4" ry="0.95" fill="var(--hl-forest)" transform="rotate(-18 17 21.5)" />
      <ellipse cx="9"  cy="17" rx="3"   ry="0.85" fill="var(--hl-forest)" transform="rotate(22 12 16.5)"  />
      <ellipse cx="21" cy="17" rx="3"   ry="0.85" fill="var(--hl-forest)" transform="rotate(-22 18 16.5)" />
      <ellipse cx="11" cy="12" rx="2.4" ry="0.75" fill="var(--hl-forest)" transform="rotate(26 13 11.5)"  />
      <ellipse cx="19" cy="12" rx="2.4" ry="0.75" fill="var(--hl-forest)" transform="rotate(-26 17 11.5)" />
      <ellipse cx="15" cy="7"  rx="1.4" ry="2"    fill="var(--hl-bloom)" />
    </svg>
  )
}
