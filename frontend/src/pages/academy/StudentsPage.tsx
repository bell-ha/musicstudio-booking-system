import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { daysUntil, STATUS_LABEL } from '../../academy/format'
import type { Catalog, StudentSummary } from '../../academy/types'
import { isManager } from '../../labels'
import { todayIn, zoneOf } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

type Member = { membershipId: number; name: string; role: string }

/** 수강 칩: 과목·강사·종료일 D-n. 일시정지와 기간 지남은 배지로 */
export function EnrollmentChip({ e, today }: { e: StudentSummary['enrollments'][number]; today: string }) {
  const left = e.endsOn ? daysUntil(e.endsOn, today) : null
  return (
    <span className="chip">
      {e.subjectName} · {e.teacherName}
      {e.status === 'PAUSED' && <span className="badge">{STATUS_LABEL.PAUSED}</span>}
      {e.status === 'ACTIVE' && left !== null && (left < 0
        ? <span className="badge badge-warning">기간 지남</span>
        : left <= 14 && <span className="badge badge-warning">D-{left}</span>)}
      {e.totalSessions !== null && ` · ${e.totalSessions}회`}
    </span>
  )
}

/**
 * UC-44 원생 목록과 만료 임박(관리자), UC-45 담당 학생(강사, 그리고 직접 가르치는 관리자).
 * mine이면 내가 담당하는 진행 중 수강이 있는 원생만. 필터는 서버가 한다.
 */
export function StudentsPage({ mine = false }: { mine?: boolean }) {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [students, setStudents] = useState<StudentSummary[] | null>(null)
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [teachers, setTeachers] = useState<Member[]>([])
  const [filter, setFilter] = useState({ q: '', subjectId: '', teacherId: '', expiring: false, inactive: false })
  const [message, setMessage] = useState('')
  const admin = Boolean(me && isManager(me.role)) && !mine

  useEffect(() => {
    if (!admin) return
    api<Catalog>(`/organizations/${orgId}/academy/catalog`).then(setCatalog).catch(() => {})
    api<Member[]>(`/organizations/${orgId}/members?status=ACTIVE`)
      .then((list) => setTeachers(list.filter((m) => m.role !== 'STUDENT')))
      .catch(() => {})
  }, [admin, orgId])

  useEffect(() => {
    if (!me) return
    const params = new URLSearchParams()
    if (mine) params.set('mine', 'true')
    if (filter.q.trim()) params.set('q', filter.q.trim())
    if (filter.subjectId) params.set('subjectId', filter.subjectId)
    if (filter.teacherId) params.set('teacherId', filter.teacherId)
    if (filter.expiring) params.set('expiringWithinDays', '14')
    if (filter.inactive) params.set('active', 'false')
    let current = true
    // 검색어를 칠 때마다 요청하지 않도록 잠깐 기다린다
    const timer = setTimeout(() => {
      api<StudentSummary[]>(`/organizations/${orgId}/academy/students?${params}`)
        .then((list) => current && setStudents(list))
        .catch((error) => current && setMessage(errorMessage(error)))
    }, filter.q ? 250 : 0)
    return () => { current = false; clearTimeout(timer) }
  }, [me, orgId, mine, filter])

  if (me === undefined) return <main className="page" />
  if (me === null || (!mine && !isManager(me.role))) return <NotMember />

  const today = todayIn(zoneOf(me.timezone))
  const set = (patch: Partial<typeof filter>) => setFilter((f) => ({ ...f, ...patch }))

  return (
    <main className="page">
      <header className="app-bar">
        <h1>{mine ? '담당 학생' : '원생'}</h1>
        {admin && <Link className="button button-primary" to={`/orgs/${orgId}/academy/students/new`}>원생 등록</Link>}
      </header>

      <div className="form">
        <input className="input-readonly" type="search" aria-label="이름 검색" placeholder="이름으로 찾기"
          value={filter.q} onChange={(e) => set({ q: e.target.value })} />
        {admin && (
          <div className="form-row wrap">
            <select aria-label="과목" value={filter.subjectId} onChange={(e) => set({ subjectId: e.target.value })}>
              <option value="">모든 과목</option>
              {catalog?.subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <select aria-label="강사" value={filter.teacherId} onChange={(e) => set({ teacherId: e.target.value })}>
              <option value="">모든 강사</option>
              {teachers.map((t) => <option key={t.membershipId} value={t.membershipId}>{t.name}</option>)}
            </select>
            <label className="check"><input type="checkbox" checked={filter.expiring} onChange={(e) => set({ expiring: e.target.checked })} />만료 임박 (14일)</label>
            <label className="check"><input type="checkbox" checked={filter.inactive} onChange={(e) => set({ inactive: e.target.checked })} />비활성 원생</label>
          </div>
        )}
      </div>

      {message && <p className="alert" role="alert">{message}</p>}
      {students?.length === 0 && (
        <p className="card empty section">{mine ? '지금 맡고 있는 수강이 없어요.' : '조건에 맞는 원생이 없어요.'}</p>
      )}
      <ul className="list section">
        {students?.map((s) => (
          <li key={s.id}>
            <Link className="card card-link" to={`/orgs/${orgId}/academy/students/${s.id}`}>
              <p className="card-title">
                {s.name}
                {s.birthYear && <span className="card-meta"> {s.birthYear}년생</span>}
                {!s.active && <span className="badge">비활성</span>}
              </p>
              <p className="card-meta tnum">{s.phone ?? '연락처 없음'}{s.linked && ' · 계정 연결됨'}</p>
              {s.enrollments.length > 0 && (
                <div className="chips">{s.enrollments.map((e) => <EnrollmentChip key={e.id} e={e} today={today} />)}</div>
              )}
            </Link>
          </li>
        ))}
      </ul>
      <p className="helper"><Link to={`/orgs/${orgId}`}>기관으로 돌아가기</Link></p>
    </main>
  )
}
