import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, errorMessage, token } from '../api'
import { ROLE_LABEL, TYPE_LABEL, type OrgType } from '../labels'

type MyOrganization = {
  organizationId: number
  name: string
  type: OrgType
  role: keyof typeof ROLE_LABEL
  status: 'PENDING' | 'ACTIVE' // 서버는 거절·비활성 멤버십을 주지 않는다
  modules: string[]
}

export function OrganizationsPage() {
  const navigate = useNavigate()
  const [organizations, setOrganizations] = useState<MyOrganization[] | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    api<MyOrganization[]>('/me/organizations')
      .then(setOrganizations)
      .catch((error) => {
        if (!token.get()) navigate('/login', { replace: true }) // 401이면 api가 토큰을 지운다
        else setMessage(errorMessage(error))
      })
  }, [navigate])

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
          <p>아직 속한 기관이 없어요. 기관을 만들거나 초대 링크로 들어오세요.</p>
          <Link className="button button-primary" to="/organizations/new">기관 만들기</Link>
        </div>
      )}

      {organizations && organizations.length > 0 && (
        <>
          <ul className="list">
            {organizations.map((org) => (
              <li className="card" key={org.organizationId}>
                <p className="card-title">
                  {org.name}
                  {org.status === 'PENDING' && <span className="badge">승인 대기</span>}
                </p>
                <p className="card-meta">{TYPE_LABEL[org.type]} · {ROLE_LABEL[org.role]}</p>
              </li>
            ))}
          </ul>
          <Link className="button button-primary" to="/organizations/new">기관 만들기</Link>
        </>
      )}
    </main>
  )
}
