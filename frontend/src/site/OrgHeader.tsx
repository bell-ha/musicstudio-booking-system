/**
 * 기관 머리 (DESIGN 0.2 4절). 기관 홈, 공개 페이지, 사이트 꾸미기 미리보기가 같이 쓴다.
 * 포인트 색을 면으로 쓰는 유일한 곳이다. 누를 것을 두지 않고, 로고는 흰 타일 안에 둔다.
 */
export function OrgHeader({ name, subtitle, logoUrl, intro }: {
  name: string
  subtitle: string
  logoUrl: string | null
  intro?: string
}) {
  return (
    <header className="hero">
      <span className="hero-logo" aria-hidden="true">
        {logoUrl ? <img src={logoUrl} alt="" /> : name.slice(0, 1)}
      </span>
      <div className="hero-text">
        <h1>{name}</h1>
        <p>{subtitle}</p>
        {intro && <p className="hero-intro">{intro}</p>}
      </div>
    </header>
  )
}
