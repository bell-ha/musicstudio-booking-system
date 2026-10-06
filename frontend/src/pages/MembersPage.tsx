import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api, errorMessage } from '../api'
import { isManager, ROLE_LABEL, type MemberStatus, type Role } from '../labels'
import { useMyOrganization } from '../useMyOrganization'
import { NotMember } from './OrganizationHomePage'

type Member = {
  membershipId: number
  userId: number
  name: string
  email?: string | null
  role: Role
  status: MemberStatus
  profile: Record<string, string>
  createdAt: string
}

const TABS: { status: MemberStatus; label: string }[] = [
  { status: 'PENDING', label: '승인 대기' },
  { status: 'ACTIVE', label: '멤버' },
  { status: 'INACTIVE', label: '비활성' },
  { status: 'REJECTED', label: '거절됨' },
]

export function MembersPage() {
  const { orgId } = useParams()
  // 소유자·관리자를 바꾸면 그게 나 자신일 수 있다. 그때는 내 역할을 다시 읽는다
  const [meVersion, setMeVersion] = useState(0)
  const me = useMyOrganization(orgId, meVersion)
  const [status, setStatus] = useState<MemberStatus>('PENDING')
  const [members, setMembers] = useState<Member[] | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    let current = true // 탭을 빨리 바꾸면 지난 탭의 응답이 늦게 와서 덮어쓸 수 있다
    api<Member[]>(`/organizations/${orgId}/members?status=${status}`)
      .then((list) => current && setMembers(list))
      .catch((error) => current && setMessage(errorMessage(error)))
    return () => { current = false }
  }, [me, orgId, status])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  // 소유자·관리자를 대상으로 하거나 소유자·관리자로 만드는 변경은 소유자만 한다
  const canChange = (target: Member) => me.role === 'OWNER' || !isManager(target.role)
  const roleOptions: Role[] = me.role === 'OWNER' ? ['OWNER', 'MANAGER', 'TEACHER', 'STUDENT'] : ['TEACHER', 'STUDENT']

  async function change(target: Member, body: { role: Role } | { status: MemberStatus }, confirmText?: string) {
    if (confirmText && !window.confirm(confirmText)) return
    setMessage('')
    try {
      const updated = await api<Member>(`/organizations/${orgId}/members/${target.membershipId}`, { method: 'PATCH', body })
      // 상태가 바뀐 멤버는 지금 탭에서 빠진다
      if (isManager(target.role)) setMeVersion((v) => v + 1)
      setMembers((list) => list && (updated.status === status
        ? list.map((m) => (m.membershipId === updated.membershipId ? updated : m))
        : list.filter((m) => m.membershipId !== updated.membershipId)))
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  function selectTab(next: MemberStatus) {
    setMembers(null)
    setMessage('')
    setStatus(next)
  }

  return (
    <main className="page page-wide">
      <h1>멤버 관리</h1>
      <div className="tabs" role="tablist">
        {TABS.map((tab) => (
          <button key={tab.status} role="tab" aria-selected={tab.status === status}
            className="tab" onClick={() => selectTab(tab.status)}>
            {tab.label}
          </button>
        ))}
      </div>

      {message && <p className="alert" role="alert">{message}</p>}
      {members?.length === 0 && <p className="card empty">해당하는 멤버가 없어요.</p>}

      {members && members.length > 0 && <h2 className="group-title">{members.length}명</h2>}
      <ul className="group">
        {members?.map((m) => (
          <li className="member-row" key={m.membershipId}>
            <div className="row">
              <span className="avatar" aria-hidden="true">{m.name.slice(0, 1)}</span>
              <span className="row-text">
                <span className="row-title">{m.name} <span className="badge">{ROLE_LABEL[m.role]}</span></span>
                <span className="row-meta">{m.email ?? '이메일 없음'}</span>
                {Object.entries(m.profile).map(([key, value]) => (
                  <span className="row-meta" key={key}>{key}: {value}</span>
                ))}
              </span>
            </div>

            {canChange(m) && (
              <div className="actions">
                {m.status === 'PENDING' && (
                  <>
                    <button className="button" onClick={() => change(m, { status: 'ACTIVE' })}>승인</button>
                    <button className="button button-danger"
                      onClick={() => change(m, { status: 'REJECTED' }, `${m.name}님의 신청을 거절할까요?`)}>거절</button>
                  </>
                )}
                {m.status === 'ACTIVE' && (
                  <>
                    <select className="select" aria-label={`${m.name} 역할`} value={m.role}
                      onChange={(e) => change(m, { role: e.target.value as Role })}>
                      {roleOptions.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                    </select>
                    <button className="button button-danger"
                      onClick={() => change(m, { status: 'INACTIVE' }, `${m.name}님을 비활성화할까요? 기존 예약은 그대로 남아요.`)}>
                      비활성화
                    </button>
                  </>
                )}
                {m.status === 'INACTIVE' && (
                  <button className="button" onClick={() => change(m, { status: 'ACTIVE' })}>다시 활성화</button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
    </main>
  )
}
