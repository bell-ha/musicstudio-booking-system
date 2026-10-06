import { Link, useParams } from 'react-router'
import { isManager, ROLE_LABEL } from '../labels'
import { useMyOrganization } from '../useMyOrganization'

const MANAGER_MENU = [
  { to: 'members', title: '멤버 관리', meta: '가입 신청 승인, 역할 변경, 비활성화' },
  { to: 'invite', title: '초대 링크', meta: '역할을 정해 한 번 쓰는 링크를 만들어요' },
  { to: 'join-code', title: '가입 코드', meta: '코드를 받은 사람이 신청하면 승인해요' },
]

export function OrganizationHomePage() {
  const { orgId } = useParams()
  const org = useMyOrganization(orgId)

  if (org === undefined) return <main className="page" />
  if (org === null) return <NotMember />

  return (
    <main className="page">
      <h1>{org.name}</h1>
      <p className="card-meta">내 역할: {ROLE_LABEL[org.role]}</p>
      {isManager(org.role) ? (
        <ul className="list section">
          {MANAGER_MENU.map((item) => (
            <li key={item.to}>
              <Link className="card card-link" to={item.to}>
                <p className="card-title">{item.title}</p>
                <p className="card-meta">{item.meta}</p>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="card empty section">연습실 예약과 수강 정보는 곧 여기에 보여요.</p>
      )}
      <p className="helper"><Link to="/">내 기관으로</Link></p>
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
