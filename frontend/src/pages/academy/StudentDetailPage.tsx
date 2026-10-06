import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, errorMessage, fieldErrors } from '../../api'
import { daysUntil, formatPhone, periodEnd, productTerms, STATUS_LABEL, won } from '../../academy/format'
import { Timeline } from '../../academy/Timeline'
import type { Catalog, EnrollmentDetail, LessonRecord, StudentDetail } from '../../academy/types'
import { Field } from '../../Field'
import { isManager, type Role } from '../../labels'
import { dateLabel, todayIn, zoneOf } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

type Member = { membershipId: number; name: string; role: Role }
type Action = 'pause' | 'resume' | 'end' | 'refund' | 'extend' | 'change-teacher'

/** 상태별로 할 수 있는 동작 (UC-42 규칙). 서버도 같은 표로 막는다 */
const ACTIONS: Record<EnrollmentDetail['status'], Action[]> = {
  ACTIVE: ['pause', 'extend', 'change-teacher', 'end', 'refund'],
  PAUSED: ['resume', 'extend', 'change-teacher', 'end', 'refund'],
  ENDED: [],
  REFUNDED: [],
}
const ACTION_LABEL: Record<Action, string> = {
  pause: '일시정지', resume: '다시 시작', end: '종료', refund: '환불', extend: '연장', 'change-teacher': '강사 변경',
}

/** UC-47 원생 상세. 관리자는 정보·계정 연결·수강 관리, 담당 강사는 레슨 기록 쓰기 */
export function StudentDetailPage() {
  const { orgId, studentId } = useParams()
  const me = useMyOrganization(orgId)
  const [detail, setDetail] = useState<StudentDetail | null>(null)
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [members, setMembers] = useState<Member[]>([])
  const [open, setOpen] = useState<{ id: number; action: 'extend' | 'change-teacher' } | 'enroll' | null>(null)
  const [editingRecord, setEditingRecord] = useState<number | null>(null)
  const [writing, setWriting] = useState<number | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [refresh, setRefresh] = useState(0)
  const manager = Boolean(me && isManager(me.role))

  useEffect(() => {
    if (!me) return
    let current = true
    api<StudentDetail>(`/organizations/${orgId}/academy/students/${studentId}`)
      .then((d) => current && setDetail(d))
      .catch((error) => current && setMessage(errorMessage(error)))
    return () => { current = false }
  }, [me, orgId, studentId, refresh])

  useEffect(() => {
    if (!manager) return
    api<Catalog>(`/organizations/${orgId}/academy/catalog`).then(setCatalog).catch(() => {})
    api<Member[]>(`/organizations/${orgId}/members?status=ACTIVE`).then(setMembers).catch(() => {})
  }, [manager, orgId])

  if (me === undefined) return <main className="page" />
  if (me === null || !['OWNER', 'MANAGER', 'TEACHER'].includes(me.role)) return <NotMember />
  if (!detail) return <main className="page">{message && <p className="alert" role="alert">{message}</p>}</main>

  const { student, enrollments, records } = detail
  const today = todayIn(zoneOf(me.timezone))
  const teachers = members.filter((m) => m.role !== 'STUDENT')
  // 다른 원생에 이미 연결된 계정은 서버가 409 ACCOUNT_ALREADY_LINKED로 막는다
  const studentAccounts = members.filter((m) => m.role === 'STUDENT')
  // 내가 담당하는 진행 중 수강. membershipId를 모르면 진행 중인 수강 모두에 쓰기 칸을 두고 서버의 403에 맡긴다
  const canWrite = (e: EnrollmentDetail) => (e.status === 'ACTIVE' || e.status === 'PAUSED')
    && (me.membershipId === undefined || e.teacherMembershipId === me.membershipId)

  async function run(request: Promise<unknown>) {
    setErrors({})
    setMessage('')
    try {
      await request
      setOpen(null)
      setWriting(null)
      setEditingRecord(null)
      setRefresh((v) => v + 1)
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
      // 낡은 화면이었으면 최신 상태를 다시 받아 보여 준다
      if (error instanceof ApiError && error.problem.code === 'CONFLICTING_UPDATE') setRefresh((v) => v + 1)
    }
  }

  const base = `/organizations/${orgId}/academy`

  function act(e: EnrollmentDetail, action: Action, body?: object) {
    if ((action === 'end' || action === 'refund')
      && !window.confirm(`${e.subjectName} 수강을 ${ACTION_LABEL[action]} 처리할까요? 되돌릴 수 없어요.`)) return
    // 화면에 보이는 버전을 함께 보낸다. 그사이 다른 관리자가 바꿨으면 409 CONFLICTING_UPDATE (이중 연장 방지)
    run(api(`${base}/enrollments/${e.id}/${action}`, { method: 'POST', body: { version: e.version, ...body } }))
  }

  function link(membershipId: string) {
    run(api(`${base}/students/${student.id}/account`, { method: 'PUT', body: { membershipId: membershipId ? Number(membershipId) : null } }))
  }

  function enroll(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const f = new FormData(event.currentTarget)
    run(api(`${base}/enrollments`, {
      method: 'POST',
      body: {
        studentId: student.id,
        productId: Number(f.get('productId')),
        teacherMembershipId: Number(f.get('teacherMembershipId')),
        startsOn: f.get('startsOn'),
      },
    }))
  }

  function saveRecord(event: FormEvent<HTMLFormElement>, enrollmentId: number, record?: LessonRecord) {
    event.preventDefault()
    const f = new FormData(event.currentTarget)
    const text = (k: string) => String(f.get(k) ?? '').trim() || null
    const body = {
      enrollmentId,
      lessonDate: f.get('lessonDate'),
      progress: text('progress'),
      homework: text('homework'),
      memo: text('memo'),
      visibleToStudent: f.get('visibleToStudent') === 'on',
    }
    run(record
      ? api(`${base}/lesson-records/${record.id}`, { method: 'PATCH', body })
      : api(`${base}/lesson-records`, { method: 'POST', body }))
  }

  const recordForm = (enrollmentId: number, record?: LessonRecord) => (
    <form className="form section" onSubmit={(e) => saveRecord(e, enrollmentId, record)}>
      <Field id={`date-${enrollmentId}`} label="레슨 날짜" error={errors.lessonDate}>
        <input id={`date-${enrollmentId}`} name="lessonDate" type="date" defaultValue={record?.lessonDate ?? today} required />
      </Field>
      <Field id={`progress-${enrollmentId}`} label="진도"><textarea id={`progress-${enrollmentId}`} name="progress" rows={2} defaultValue={record?.progress ?? ''} /></Field>
      <Field id={`homework-${enrollmentId}`} label="숙제"><textarea id={`homework-${enrollmentId}`} name="homework" rows={2} defaultValue={record?.homework ?? ''} /></Field>
      <Field id={`memo-${enrollmentId}`} label="메모"><textarea id={`memo-${enrollmentId}`} name="memo" rows={2} defaultValue={record?.memo ?? ''} /></Field>
      <label className="check"><input type="checkbox" name="visibleToStudent" defaultChecked={record?.visibleToStudent ?? true} />학생에게 공개 (진도·숙제·메모 모두)</label>
      <div className="actions">
        <button className="button">저장</button>
        <button className="button" type="button" onClick={() => { setWriting(null); setEditingRecord(null) }}>취소</button>
      </div>
    </form>
  )

  return (
    <main className="page">
      <header className="app-bar">
        <h1>{student.name}</h1>
        {manager && <Link className="button" to={`/orgs/${orgId}/academy/students/${student.id}/edit`}>정보 수정</Link>}
      </header>

      <div className="card">
        <p className="card-meta">
          {student.birthYear ? `${student.birthYear}년생` : '출생연도 없음'}
          {!student.active && <span className="badge">비활성</span>}
        </p>
        <p className="tnum">연락처 {formatPhone(student.phone) ?? '없음'}</p>
        {(student.guardianName || student.guardianPhone) && (
          <p className="tnum">보호자 {student.guardianName ?? ''} {formatPhone(student.guardianPhone) ?? ''}</p>
        )}
        {student.memo && <p className="card-meta">메모: {student.memo}</p>}
        {manager && (
          <Field id="account" label="학생 계정 연결">
            <select id="account" value={student.membershipId ?? ''} onChange={(e) => link(e.target.value)}>
              <option value="">연결 안 함</option>
              {studentAccounts.map((m) => <option key={m.membershipId} value={m.membershipId}>{m.name}</option>)}
            </select>
          </Field>
        )}
      </div>

      {message && <p className="alert section" role="alert">{message}</p>}

      <h2 className="section-title">수강</h2>
      {enrollments.length === 0 && <p className="card empty">아직 수강이 없어요.</p>}
      <ul className="list">
        {enrollments.map((e) => {
          const left = e.endsOn ? daysUntil(e.endsOn, today) : null
          return (
            <li key={e.id} className={e.status === 'ENDED' || e.status === 'REFUNDED' ? 'card card-muted' : 'card'}>
              <p className="card-title">
                {e.subjectName} · {e.productName}
                <span className="badge">{STATUS_LABEL[e.status]}</span>
                {e.status === 'ACTIVE' && left !== null && left < 0 && <span className="badge badge-warning">기간 지남</span>}
              </p>
              <p className="card-meta tnum">
                {e.teacherName} · {dateLabel(e.startsOn)}부터 {e.endsOn ? `${dateLabel(e.endsOn)}까지` : `총 ${e.totalSessions}회`}
                {e.pausedAt && ` · ${dateLabel(e.pausedAt)}부터 정지`}
                {e.price !== null && ` · ${won(e.price)}`}
              </p>

              {manager && ACTIONS[e.status].length > 0 && (
                <div className="actions">
                  {ACTIONS[e.status].map((a) => (
                    <button key={a} className={a === 'end' || a === 'refund' ? 'button button-danger' : 'button'}
                      onClick={() => (a === 'extend' || a === 'change-teacher') ? setOpen({ id: e.id, action: a }) : act(e, a)}>
                      {ACTION_LABEL[a]}
                    </button>
                  ))}
                </div>
              )}
              {typeof open === 'object' && open?.id === e.id && open.action === 'extend' && (
                <form className="form-row section" onSubmit={(ev) => {
                  ev.preventDefault()
                  const n = Number(new FormData(ev.currentTarget).get('n'))
                  act(e, 'extend', e.kind === 'PERIOD' ? { months: n } : { sessions: n })
                }}>
                  <input name="n" type="number" min={1} defaultValue={1} aria-label={e.kind === 'PERIOD' ? '연장할 개월' : '추가할 횟수'} />
                  <span>{e.kind === 'PERIOD' ? '개월' : '회'}</span>
                  <button className="button">연장</button>
                </form>
              )}
              {typeof open === 'object' && open?.id === e.id && open.action === 'change-teacher' && (
                <form className="form-row section" onSubmit={(ev) => {
                  ev.preventDefault()
                  act(e, 'change-teacher', { teacherMembershipId: Number(new FormData(ev.currentTarget).get('t')) })
                }}>
                  <select name="t" aria-label="새 강사" defaultValue={e.teacherMembershipId}>
                    {teachers.map((t) => <option key={t.membershipId} value={t.membershipId}>{t.name}</option>)}
                  </select>
                  <button className="button">바꾸기</button>
                </form>
              )}

              {canWrite(e) && me.role !== 'STUDENT' && (writing === e.id ? recordForm(e.id) : (
                <div className="actions"><button className="button" onClick={() => setWriting(e.id)}>레슨 기록 쓰기</button></div>
              ))}
            </li>
          )
        })}
      </ul>

      {manager && student.active && (open === 'enroll' ? (
        <EnrollForm orgId={orgId!} catalog={catalog} teachers={teachers} today={today} onSubmit={enroll} onCancel={() => setOpen(null)} />
      ) : (
        <button className="button button-primary button-block" onClick={() => setOpen('enroll')}>수강 등록</button>
      ))}

      <h2 className="section-title">이력</h2>
      <Timeline enrollments={enrollments} records={records} renderRecordActions={(r) => r.mine && (
        editingRecord === r.id ? recordForm(r.enrollmentId, r) : (
          <div className="actions">
            <button className="button" onClick={() => setEditingRecord(r.id)}>수정</button>
            <button className="button button-danger" onClick={() => window.confirm('이 기록을 지울까요?')
              && run(api(`${base}/lesson-records/${r.id}`, { method: 'DELETE' }))}>삭제</button>
          </div>
        )
      )} />

      <p className="helper">
        <Link to={manager ? `/orgs/${orgId}/academy/students` : `/orgs/${orgId}/academy/my-students`}>목록으로</Link>
      </p>
    </main>
  )
}

/** UC-42. 상품·강사·시작일을 고르면 종료일 또는 총 횟수를 미리 보여 준다. 저장 결과는 서버 값이다 */
function EnrollForm({ orgId, catalog, teachers, today, onSubmit, onCancel }: {
  orgId: string
  catalog: Catalog | null
  teachers: Member[]
  today: string
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onCancel: () => void
}) {
  const [productId, setProductId] = useState('')
  const [startsOn, setStartsOn] = useState(today)
  const product = catalog?.products.find((p) => String(p.id) === productId)

  if (catalog && catalog.products.length === 0) {
    return <p className="card empty">먼저 <Link to={`/orgs/${orgId}/academy/catalog`}>과목·상품</Link>에서 상품을 만들어 주세요.</p>
  }

  return (
    <form className="form card" onSubmit={onSubmit}>
      <p className="card-title">수강 등록</p>
      <Field id="productId" label="상품">
        <select id="productId" name="productId" value={productId} onChange={(e) => setProductId(e.target.value)} required>
          <option value="" disabled>상품을 골라 주세요</option>
          {catalog?.subjects.map((s) => (
            <optgroup key={s.id} label={s.name}>
              {catalog.products.filter((p) => p.subjectId === s.id).map((p) => (
                <option key={p.id} value={p.id}>{p.name} ({productTerms(p)})</option>
              ))}
            </optgroup>
          ))}
        </select>
      </Field>
      <Field id="teacherMembershipId" label="담당 강사">
        <select id="teacherMembershipId" name="teacherMembershipId" required>
          {teachers.map((t) => <option key={t.membershipId} value={t.membershipId}>{t.name}</option>)}
        </select>
      </Field>
      <Field id="startsOn" label="시작일">
        <input id="startsOn" name="startsOn" type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} required />
      </Field>
      {product && (
        <p className="notice tnum">
          {product.kind === 'PERIOD'
            ? `${dateLabel(startsOn)}부터 ${dateLabel(periodEnd(startsOn, product.periodMonths!))}까지`
            : `총 ${product.sessionCount}회`} · {won(product.price)}
        </p>
      )}
      <div className="actions">
        <button className="button button-primary">등록</button>
        <button className="button" type="button" onClick={onCancel}>취소</button>
      </div>
    </form>
  )
}
