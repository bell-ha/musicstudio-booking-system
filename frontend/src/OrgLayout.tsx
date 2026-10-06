import { useEffect, useState } from 'react'
import { House, LayoutGrid, LogOut } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate, useParams } from 'react-router'
import { api, token } from './api'
import { ROLE_LABEL, type MyOrganization } from './labels'
import { orgMenu } from './orgMenu'
import type { OrgOutlet } from './orgSite'
import { colorStyle, type Site } from './site/site'

/**
 * 기관 안의 모든 화면을 감싸는 틀 (DESIGN 5절).
 * 위: 앱 바(로고, 기관 이름, 기관 전환, 로그아웃). 1024px 이상: 왼쪽 메뉴. 그보다 좁으면: 아래 탭 바.
 * 기관 색(FR-SITE-02)은 이 틀의 루트에 CSS 변수로 건다. 그래서 기관 안의 모든 화면에 적용된다.
 * 각 화면에 "기관으로 돌아가기"를 두지 않아도 어디서든 다른 메뉴로 갈 수 있다.
 */
export function OrgLayout() {
  const { orgId } = useParams()
  const navigate = useNavigate()
  const [orgs, setOrgs] = useState<MyOrganization[]>()

  const [site, setSite] = useState<Site>()
  const [siteVersion, setSiteVersion] = useState(0)

  useEffect(() => {
    api<MyOrganization[]>('/me/organizations').then(setOrgs).catch(() => setOrgs([]))
  }, [orgId])

  useEffect(() => {
    api<Site>(`/organizations/${orgId}/site`).then(setSite).catch(() => setSite(undefined))
  }, [orgId, siteVersion])

  const active = orgs?.filter((o) => o.status === 'ACTIVE') ?? []
  const org = active.find((o) => String(o.organizationId) === orgId)
  const groups = org ? orgMenu(org) : []
  const tabs = groups.flatMap((g) => g.items).filter((i) => i.tab)
  const base = `/orgs/${orgId}`

  function logout() {
    token.clear()
    navigate('/login')
  }

  return (
    <div className="shell" style={colorStyle(site?.color)}>
      <header className="shell-bar">
        {/* 로고. 올리기 전에는 이름 첫 글자 */}
        <Link to={base} className="shell-logo" aria-hidden="true" tabIndex={-1}>
          {site?.logoUrl ? <img src={site.logoUrl} alt="" /> : org?.name.slice(0, 1)}
        </Link>
        {/* 기관이 여럿이면 이름 자리가 곧 기관 전환 */}
        {active.length > 1 ? (
          <select aria-label="기관 바꾸기" className="shell-org shell-switch" value={orgId}
            onChange={(e) => navigate(`/orgs/${e.target.value}`)}>
            {active.map((o) => <option key={o.organizationId} value={o.organizationId}>{o.name}</option>)}
          </select>
        ) : (
          <Link to={base} className="shell-org">{org?.name ?? ' '}</Link>
        )}
        <span className="shell-spacer" />
        {org && <span className="shell-role">{ROLE_LABEL[org.role]}</span>}
        <button type="button" className="shell-link" onClick={logout} aria-label="로그아웃" title="로그아웃"><LogOut size={20} /></button>
      </header>

      <div className="shell-body">
        {groups.length > 0 && (
          <nav className="shell-side" aria-label="기관 메뉴">
            <NavLink to={base} end className="side-link"><House size={20} />홈</NavLink>
            {groups.map((g) => (
              <div key={g.title} className="side-group">
                <p className="side-title">{g.title}</p>
                {g.items.map((i) => (
                  <NavLink key={i.to} to={`${base}/${i.to}`} end className="side-link"><i.icon size={20} />{i.title}</NavLink>
                ))}
              </div>
            ))}
            <Link to="/" className="side-link side-foot">내 기관 목록</Link>
          </nav>
        )}
        <div className="shell-main">
          <Outlet context={{ site, reloadSite: () => setSiteVersion((v) => v + 1) } satisfies OrgOutlet} />
        </div>
      </div>

      {tabs.length > 0 && (
        <nav className="shell-tabs" aria-label="주요 메뉴">
          {tabs.map((i) => (
            <NavLink key={i.to} to={`${base}/${i.to}`} end className="tab-link"><i.icon size={24} strokeWidth={1.8} />{i.tab}</NavLink>
          ))}
          <NavLink to={base} end className="tab-link"><LayoutGrid size={24} strokeWidth={1.8} />전체</NavLink>
        </nav>
      )}
    </div>
  )
}
