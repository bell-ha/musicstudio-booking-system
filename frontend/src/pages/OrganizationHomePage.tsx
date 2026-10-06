import { ChevronRight } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ROLE_LABEL, TYPE_LABEL } from '../labels'
import { orgMenu } from '../orgMenu'
import { useMyOrganization } from '../useMyOrganization'

/** 기관 홈: 메뉴를 학원·연습실·기관 관리로 묶어 보여 준다. 같은 목록이 왼쪽 메뉴에도 있다 (orgMenu) */
export function OrganizationHomePage() {
  const { orgId } = useParams()
  const org = useMyOrganization(orgId)

  if (org === undefined) return <main className="page page-wide" />
  if (org === null) return <NotMember />

  const groups = orgMenu(org)
  return (
    <main className="page page-wide">
      <header className="hero">
        <span className="hero-logo" aria-hidden="true">{org.name.slice(0, 1)}</span>
        <div>
          <h1>{org.name}</h1>
          <p>{TYPE_LABEL[org.type]} · {ROLE_LABEL[org.role]}</p>
        </div>
      </header>
      {groups.length > 0 ? (
        <div className="groups">
          {groups.map((g) => (
            <section key={g.title}>
              <h2 className="group-title">{g.title}</h2>
              <ul className="group">
                {g.items.map((item) => (
                  <li key={item.to}>
                    <Link className="row" to={item.to}>
                      <span className="row-icon"><item.icon size={20} /></span>
                      <span className="row-text">
                        <span className="row-title">{item.title}</span>
                        <span className="row-meta">{item.meta}</span>
                      </span>
                      <ChevronRight className="row-chevron" size={20} />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      ) : (
        <p className="card empty section">이 기관에서 쓸 수 있는 메뉴가 아직 없어요.</p>
      )}
    </main>
  )
}

export function NotMember() {
  return (
    <main className="page">
      <p className="alert" role="alert">이 기관의 멤버가 아니거나 아직 승인되지 않았어요.</p>
      <p className="helper"><Link to="/">내 기관으로</Link></p>
    </main>
  )
}
