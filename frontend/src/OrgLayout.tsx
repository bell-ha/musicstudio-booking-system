import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate, useParams } from 'react-router'
import { api, token } from './api'
import { ROLE_LABEL, type MyOrganization } from './labels'
import { orgMenu } from './orgMenu'

/**
 * 기관 안의 모든 화면을 감싸는 틀 (DESIGN 5절).
 * 위: 앱 바(기관 이름, 기관 전환, 로그아웃). 1024px 이상: 왼쪽 메뉴. 그보다 좁으면: 아래 탭 바.
 * 각 화면에 "기관으로 돌아가기"를 두지 않아도 어디서든 다른 메뉴로 갈 수 있다.
 */
export function OrgLayout() {
  const { orgId } = useParams()
  const navigate = useNavigate()
  const [orgs, setOrgs] = useState<MyOrganization[]>()

  useEffect(() => {
    api<MyOrganization[]>('/me/organizations').then(setOrgs).catch(() => setOrgs([]))
  }, [orgId])

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
    <div className="shell">
      <header className="shell-bar">
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
        <button type="button" className="shell-link" onClick={logout}>로그아웃</button>
      </header>

      <div className="shell-body">
        {groups.length > 0 && (
          <nav className="shell-side" aria-label="기관 메뉴">
            <NavLink to={base} end className="side-link">홈</NavLink>
            {groups.map((g) => (
              <div key={g.title} className="side-group">
                <p className="side-title">{g.title}</p>
                {g.items.map((i) => <NavLink key={i.to} to={`${base}/${i.to}`} end className="side-link">{i.title}</NavLink>)}
              </div>
            ))}
            <Link to="/" className="side-link side-foot">내 기관 목록</Link>
          </nav>
        )}
        <div className="shell-main"><Outlet /></div>
      </div>

      {tabs.length > 0 && (
        <nav className="shell-tabs" aria-label="주요 메뉴">
          {tabs.map((i) => <NavLink key={i.to} to={`${base}/${i.to}`} end className="tab-link">{i.tab}</NavLink>)}
          <NavLink to={base} end className="tab-link">전체</NavLink>
        </nav>
      )}
    </div>
  )
}
