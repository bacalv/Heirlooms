export function WaxSealOlive({ size = 20, className = '' }) {
  return (
    <svg
      viewBox="0 0 20 32"
      width={size}
      height={Math.round(size * 1.6)}
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={`text-bloom ${className}`}
      fill="currentColor"
    >
      <path d="M 10 2.5 C 5 3, 2.5 10, 2.5 18 C 2.5 25.5, 5.5 29.5, 10 29.5 C 14.5 29.5, 17.5 25.5, 17.5 18 C 17.5 10, 15 3, 10 2.5 Z" />
    </svg>
  )
}
