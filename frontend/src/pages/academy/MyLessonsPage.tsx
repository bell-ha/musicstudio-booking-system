import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api, ApiError, errorMessage } from '../../api'
import { STATUS_LABEL } from '../../academy/format'
import { Timeline } from '../../academy/Timeline'
import { addDays, localTime, SESSION_LABEL, slotSummary } from '../../academy/lessons'
import type { Session, StudentDetail } from '../../academy/types'
import { dateLabel, todayIn, zoneOf } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-48. 학생 본인의 수강과 공개된 레슨 기록. 학원이 계정을 연결해야 보인다 */
export function MyLessonsPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [detail, setDetail] = useState<StudentDetail | null>(null)
  const [message, setMessage] = useState('')
  const [sessions, setSessions] = useState<Session[]>([])
  const tz = zoneOf(me?.timezone)

  useEffect(() => {
    if (!me) return
    api<StudentDetail>(`/organizations/${orgId}/academy/students/me`)
      .then(setDetail)
      .catch((error) => setMessage(error instanceof ApiError && error.problem.code === 'NOT_LINKED'
        ? '학원에서 계정을 연결하면 수강 정보와 레슨 기록이 보여요. 학원에 문의해 주세요.'
        : errorMessage(error)))
    // UC-53: 지난 2주 출결과 앞으로 4주 일정 (서버는 42일까지 한 번에 준다)
    const today = todayIn(zoneOf(me.timezone))
    api<Session[]>(`/organizations/${orgId}/academy/students/me/sessions?from=${addDays(today, -14)}&to=${addDays(today, 27)}`)
      .then(setSessions).catch(() => setSessions([]))
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
                {(e.schedule.length > 0 || e.remainingSessions !== null) && (
                  <p className="card-meta tnum">
                    {e.schedule.length > 0 && `매주 ${slotSummary(e.schedule)}`}
                    {e.remainingSessions !== null && ` · 남은 ${e.remainingSessions}회`}
                  </p>
                )}
              </li>
            ))}
          </ul>
          {sessions.length > 0 && (() => {
            const today = todayIn(tz)
            const upcoming = sessions.filter((x) => x.localDate >= today)
            const past = sessions.filter((x) => x.localDate < today).reverse()
            const row = (x: Session) => (
              <li key={x.id} className="row row-compact">
                <span className="row-time tnum">{x.localDate.slice(5).replace('-', '/')}</span>
                <span className="row-text">
                  <span className="row-title">{x.subjectName} {localTime(x.startsAt, tz)}{x.kind === 'MAKEUP' && <span className="badge">보강</span>}</span>
                  {x.status === 'CANCELED' && <span className="row-meta">휴강 · {x.note}</span>}
                </span>
                {x.status !== 'SCHEDULED' && <span className="badge">{SESSION_LABEL[x.status]}</span>}
              </li>
            )
            return (
              <>
                <h2 className="group-title">다가오는 레슨</h2>
                <ul className="group">{upcoming.length > 0 ? upcoming.map(row) : <li className="row row-compact"><span className="row-meta">4주 안에 레슨이 없어요</span></li>}</ul>
                {past.length > 0 && (<><h2 className="group-title">지난 2주 출결</h2><ul className="group">{past.map(row)}</ul></>)}
              </>
            )
          })()}
          <h2 className="section-title">레슨 기록</h2>
          <Timeline enrollments={detail.enrollments} records={detail.records} />
        </>
      )}
    </main>
  )
}
