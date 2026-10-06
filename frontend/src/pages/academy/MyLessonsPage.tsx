import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api, ApiError, errorMessage } from '../../api'
import { STATUS_LABEL } from '../../academy/format'
import { Timeline } from '../../academy/Timeline'
import type { StudentDetail } from '../../academy/types'
import { dateLabel } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-48. 학생 본인의 수강과 공개된 레슨 기록. 학원이 계정을 연결해야 보인다 */
export function MyLessonsPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [detail, setDetail] = useState<StudentDetail | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!me) return
    api<StudentDetail>(`/organizations/${orgId}/academy/students/me`)
      .then(setDetail)
      .catch((error) => setMessage(error instanceof ApiError && error.problem.code === 'NOT_LINKED'
        ? '학원에서 계정을 연결하면 수강 정보와 레슨 기록이 보여요. 학원에 문의해 주세요.'
        : errorMessage(error)))
  }, [me, orgId])

  if (me === undefined) return <main className="page" />
  if (me === null) return <NotMember />

  return (
    <main className="page">
      <h1>내 수강</h1>
      {message && <p className="card empty">{message}</p>}
      {detail && (
        <>
          <ul className="list">
            {detail.enrollments.map((e) => (
              <li key={e.id} className={e.status === 'ACTIVE' || e.status === 'PAUSED' ? 'card' : 'card card-muted'}>
                <p className="card-title">{e.subjectName} <span className="badge">{STATUS_LABEL[e.status]}</span></p>
                <p className="card-meta tnum">
                  {e.teacherName} · {dateLabel(e.startsOn)}부터 {e.endsOn ? `${dateLabel(e.endsOn)}까지` : `총 ${e.totalSessions}회`}
                </p>
              </li>
            ))}
          </ul>
          <h2 className="section-title">레슨 기록</h2>
          <Timeline enrollments={detail.enrollments} records={detail.records} />
        </>
      )}
    </main>
  )
}
