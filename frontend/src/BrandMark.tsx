/** 마디(Madi) 표시: 두 마디줄 사이의 음표. favicon(public/favicon.svg)과 같은 그림 */
export function BrandMark({ size = 56 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" className="brand-mark">
      <rect width="64" height="64" rx="15" fill="var(--primary)" />
      <rect x="12" y="15" width="4" height="34" rx="2" fill="#fff" opacity=".55" />
      <rect x="48" y="15" width="4" height="34" rx="2" fill="#fff" opacity=".55" />
      <rect x="34.5" y="17" width="3.6" height="23" rx="1.8" fill="#fff" />
      <path d="M36.3 17c4.2 1.4 7.6 4.6 7.6 9.2 -1.6-2.6-4.4-3.9-7.6-4.1z" fill="#fff" />
      <ellipse cx="30.4" cy="41" rx="7.2" ry="5.4" transform="rotate(-22 30.4 41)" fill="#fff" />
    </svg>
  )
}
