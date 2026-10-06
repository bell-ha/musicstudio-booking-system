import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, errorMessage, token } from '../api'
import { ROLE_LABEL, TYPE_LABEL, type MyOrganization } from '../labels'

export function OrganizationsPage() {
  const navigate = useNavigate()
  const [organizations, setOrganizations] = useState<MyOrganization[] | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    api<MyOrganization[]>('/me/organizations')
      .then(setOrganizations)
      .catch((error) => setMessage(errorMessage(error)))
  }, [])

  function logout() {
    token.clear()
    navigate('/login', { replace: true })
  }

  return (
    <main className="page">
      <header className="app-bar">
        <h1>내 기관</h1>
        <button className="button" onClick={logout}>로그아웃</button>
      </header>

      {message && <p className="alert" role="alert">{message}</p>}

      {organizations?.length === 0 && (
        <div className="card empty">
          <p>아직 속한 기관이 없어요. 기관을 만들거나, 받은 초대 링크나 가입 코드로 들어오세요.</p>
          <Link className="button button-primary" to="/organizations/new">기관 만들기</Link>
        </div>
      )}

      {organizations && organizations.length > 0 && (
        <>
          <ul className="list">
            {organizations.map((org) => {
              const body = (
                <>
                  <p className="card-title">
                    {org.name}
                    {org.status === 'PENDING' && <span className="badge">승인 대기</span>}
                  </p>
                  <p className="card-meta">{TYPE_LABEL[org.type]} · {ROLE_LABEL[org.role]}</p>
                </>
              )
              // 승인 대기 중에는 기관의 어떤 데이터도 볼 수 없으므로 들어가지 않는다 (UC-06 규칙)
              return (
                <li key={org.organizationId}>
                  {org.status === 'ACTIVE'
                    ? <Link className="card card-link" to={`/orgs/${org.organizationId}`}>{body}</Link>
                    : <div className="card">{body}</div>}
                </li>
              )
            })}
          </ul>
          <Link className="button button-primary" to="/organizations/new">기관 만들기</Link>
        </>
      )}

      {organizations && <p className="helper"><Link to="/join">가입 코드로 신청하기</Link></p>}
    </main>
  )
}
