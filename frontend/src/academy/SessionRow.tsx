import { ChevronRight } from 'lucide-react'
import { localTime, SESSION_LABEL } from './lessons'
import type { Session } from './types'

const BADGE: Record<Session['status'], string> = {
  SCHEDULED: '', ATTENDED: 'badge badge-done', ABSENT: 'badge badge-warning', EXCUSED: 'badge', CANCELED: 'badge badge-unavailable',
}

/** 회차 한 줄: 시각, 원생·과목, (관리자) 강사, 상태. 지난 SCHEDULED는 "출결 안 함"으로 눈에 띄게 */
export function SessionRow({ session, tz, now, showTeacher, onOpen }: {
  session: Session
  /** 목록을 불러온 시각 (렌더 중에 시계를 읽지 않는다) */
  now: number
  tz: string
  showTeacher: boolean
  onOpen: () => void
}) {
  const unmarked = session.status === 'SCHEDULED' && Date.parse(session.endsAt) < now
  return (
    <button type="button" className="row row-button" onClick={onOpen}>
      <span className="row-time tnum">{localTime(session.startsAt, tz)}</span>
      <span className="row-text">
        <span className="row-title">
          {session.studentName}
          {session.kind === 'MAKEUP' && <span className="badge">보강</span>}
        </span>
        <span className="row-meta">
          {session.subjectName}
          {showTeacher && ` · ${session.teacherName}`}
          {!session.teacherActive && ' · 비활성 강사'}
        </span>
      </span>
      {unmarked
        ? <span className="badge badge-warning">출결 안 함</span>
        : session.status !== 'SCHEDULED' && <span className={BADGE[session.status]}>{SESSION_LABEL[session.status]}</span>}
      <ChevronRight className="row-chevron" size={20} />
    </button>
  )
}
