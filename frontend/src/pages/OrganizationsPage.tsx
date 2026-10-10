import { useEffect, useState } from 'react'
import { ChevronRight, KeyRound, Link2, LogOut, Plus } from 'lucide-react'
import { googleLoginEnabled, startGoogleLogin } from '../googleLogin'
import { BrandMark } from '../BrandMark'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, errorMessage, token } from '../api'
import { ROLE_LABEL, TYPE_LABEL, type MyOrganization } from '../labels'

/** UC-09 내 기관. 앱 첫 화면: 서비스 표시, 기관 목록(이름 첫 글자 아바타), 기관 만들기·가입 코드 */
export function OrganizationsPage() {
  const navigate = useNavigate()
  const [organizations, setOrganizations] = useState<MyOrganization[] | null>(null)
  const [message, setMessage] = useState('')
  const [me, setMe] = useState<{ name: string; email: string | null; googleLinked: boolean }>()
  const linked = useSearchParams()[0].get('linked') === 'google'

  useEffect(() => {
    api<MyOrganization[]>('/me/organizations')
      .then(setOrganizations)
      .catch((error) => setMessage(errorMessage(error)))
    api<{ name: string; email: string | null; googleLinked: boolean }>('/me').then(setMe).catch(() => {})
  }, [])

  function logout() {
    token.clear()
    navigate('/login', { replace: true })
  }

  return (
    <main className="page">
      <header className="app-bar">
        <div className="brand-row"><BrandMark size={36} /><span className="brand-name">마디</span></div>
        <button type="button" className="shell-link" onClick={logout} aria-label="로그아웃" title="로그아웃"><LogOut size={20} /></button>
      </header>
      <h1>내 기관</h1>

      {message && <p className="alert" role="alert">{message}</p>}
      {linked && <p className="notice-inline" role="status">구글 계정을 연결했어요. 다음부터 'Google로 계속하기'로 이 계정에 들어올 수 있어요.</p>}

      {organizations?.length === 0 && (
        <p className="page-sub">아직 속한 기관이 없어요. 기관을 만들거나, 받은 초대 링크나 가입 코드로 들어오세요.</p>
      )}

      {organizations && organizations.length > 0 && (
        <ul className="group">
          {organizations.map((org) => {
            const body = (
              <>
                <span className="avatar" aria-hidden="true">{org.name.slice(0, 1)}</span>
                <span className="row-text">
                  <span className="row-title">
                    {org.name}
                    {org.status === 'PENDING' && <span className="badge">승인 대기</span>}
                  </span>
                  <span className="row-meta">{org.status === 'PENDING' ? '관리자가 승인하면 들어갈 수 있어요' : `${TYPE_LABEL[org.type]} · ${ROLE_LABEL[org.role]}`}</span>
                </span>
              </>
            )
            // 승인 대기 중에는 기관의 어떤 데이터도 볼 수 없으므로 들어가지 않는다 (UC-06 규칙)
            return (
              <li key={org.organizationId}>
                {org.status === 'ACTIVE'
                  ? <Link className="row" to={`/orgs/${org.organizationId}`}>{body}<ChevronRight className="row-chevron" size={20} /></Link>
                  : <div className="row">{body}</div>}
              </li>
            )
          })}
        </ul>
      )}

      {organizations && (
        <>
          <h2 className="group-title">더하기</h2>
          <ul className="group">
            <li>
              <Link className="row" to="/organizations/new">
                <span className="row-icon"><Plus size={20} /></span>
                <span className="row-text"><span className="row-title">기관 만들기</span><span className="row-meta">학원이나 학교를 만들고 원장이 돼요</span></span>
                <ChevronRight className="row-chevron" size={20} />
              </Link>
            </li>
            <li>
              <Link className="row" to="/join">
                <span className="row-icon"><KeyRound size={20} /></span>
                <span className="row-text"><span className="row-title">가입 코드로 신청하기</span><span className="row-meta">학원에서 받은 코드를 넣어요</span></span>
                <ChevronRight className="row-chevron" size={20} />
              </Link>
            </li>
          </ul>

          {me && (
            <>
              <h2 className="group-title">내 계정</h2>
              <ul className="group">
                <li className="row">
                  <span className="avatar" aria-hidden="true">{me.name.slice(0, 1)}</span>
                  <span className="row-text"><span className="row-title">{me.name}</span><span className="row-meta">{me.email ?? '이메일 없음'}</span></span>
                </li>
                {googleLoginEnabled && (
                  <li>
                    {me.googleLinked ? (
                      <div className="row row-compact">
                        <span className="row-icon"><Link2 size={18} /></span>
                        <span className="row-text"><span className="row-title-plain">구글 계정 연결됨</span></span>
                      </div>
                    ) : (
                      <button type="button" className="row row-button row-compact" onClick={() => startGoogleLogin('link')}>
                        <span className="row-icon"><Link2 size={18} /></span>
                        <span className="row-text">
                          <span className="row-title">구글 계정 연결</span>
                          <span className="row-meta">연결하면 다음부터 구글로 이 계정에 들어와요</span>
                        </span>
                        <ChevronRight className="row-chevron" size={20} />
                      </button>
                    )}
                  </li>
                )}
              </ul>
            </>
          )}
        </>
      )}
    </main>
  )
}
