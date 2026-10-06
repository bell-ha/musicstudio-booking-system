import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../../api'
import { formatPhone } from '../../academy/format'
import type { StudentDetail, StudentInfo } from '../../academy/types'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { todayIn, zoneOf } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-41. 원생 등록과 수정. 수정도 모든 필드를 보낸다 (계약: PATCH가 PUT처럼 동작) */
export function StudentFormPage() {
  const { orgId, studentId } = useParams()
  const navigate = useNavigate()
  const me = useMyOrganization(orgId)
  const [student, setStudent] = useState<StudentInfo | null>(null)
  const [birthYear, setBirthYear] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!studentId || !me || !isManager(me.role)) return
    api<StudentDetail>(`/organizations/${orgId}/academy/students/${studentId}`)
      .then((d) => { setStudent(d.student); setBirthYear(d.student.birthYear ? String(d.student.birthYear) : '') })
      .catch((error) => setMessage(errorMessage(error)))
  }, [me, orgId, studentId])

  if (me === undefined || (studentId && !student && !message)) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  // 미성년이면 보호자 연락처가 필요하다고 알려 준다 (출생연도 기준 대략, 막지는 않는다)
  const minor = birthYear !== '' && Number(todayIn(zoneOf(me.timezone)).slice(0, 4)) - Number(birthYear) < 19

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const f = new FormData(event.currentTarget)
    const text = (k: string) => String(f.get(k) ?? '').trim() || null
    setErrors({})
    setMessage('')
    try {
      const saved = await api<StudentInfo>(
        studentId ? `/organizations/${orgId}/academy/students/${studentId}` : `/organizations/${orgId}/academy/students`,
        {
          method: studentId ? 'PATCH' : 'POST',
          body: {
            name: text('name'),
            birthYear: birthYear ? Number(birthYear) : null,
            phone: text('phone'),
            guardianName: text('guardianName'),
            guardianPhone: text('guardianPhone'),
            memo: text('memo'),
            active: studentId ? f.get('active') === 'on' : true,
          },
        },
      )
      navigate(`/orgs/${orgId}/academy/students/${saved.id}`, { replace: true })
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  return (
    <main className="page">
      <h1>{studentId ? '원생 정보 수정' : '원생 등록'}</h1>
      <form className="form" onSubmit={submit} noValidate>
        <Field id="name" label="이름" error={errors.name}>
          <input id="name" name="name" defaultValue={student?.name} required />
        </Field>
        <Field id="birthYear" label="출생연도 (선택)" error={errors.birthYear}>
          <input id="birthYear" type="number" min={1900} max={2100} placeholder="예: 2012" value={birthYear}
            onChange={(e) => setBirthYear(e.target.value)} />
        </Field>
        <Field id="phone" label="연락처 (선택)" error={errors.phone}>
          <input id="phone" name="phone" type="tel" inputMode="tel" defaultValue={formatPhone(student?.phone ?? null) ?? ''} placeholder="010-1234-5678" />
        </Field>
        <fieldset className="card fieldset">
          <legend className="card-title">보호자</legend>
          {minor && <p className="card-meta">미성년 원생은 보호자 연락처를 적어 주세요.</p>}
          <Field id="guardianName" label="이름" error={errors.guardianName}>
            <input id="guardianName" name="guardianName" defaultValue={student?.guardianName ?? ''} />
          </Field>
          <Field id="guardianPhone" label="연락처" error={errors.guardianPhone}>
            <input id="guardianPhone" name="guardianPhone" type="tel" inputMode="tel" defaultValue={formatPhone(student?.guardianPhone ?? null) ?? ''} />
          </Field>
        </fieldset>
        <Field id="memo" label="메모 (관리자만 봐요)" error={errors.memo}>
          <textarea id="memo" name="memo" rows={3} defaultValue={student?.memo ?? ''} />
        </Field>
        {studentId && (
          <label className="check">
            <input type="checkbox" name="active" defaultChecked={student?.active} />
            활성 원생 (끄면 새 수강을 등록할 수 없어요. 진행 중인 수강은 그대로 남아요)
          </label>
        )}
        {message && <p className="alert" role="alert">{message}</p>}
        <button className="button button-primary">{studentId ? '저장' : '등록'}</button>
      </form>
      <p className="helper">
        <Link to={studentId ? `/orgs/${orgId}/academy/students/${studentId}` : `/orgs/${orgId}/academy/students`}>돌아가기</Link>
      </p>
    </main>
  )
}
