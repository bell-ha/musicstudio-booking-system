import type { ReactNode } from 'react'
import { dateLabel } from '../practice/time'
import type { EnrollmentDetail, LessonRecord } from './types'

type Item =
  | { kind: 'start'; date: string; enrollment: EnrollmentDetail }
  | { kind: 'record'; date: string; record: LessonRecord }

/**
 * FR-AC-09. 수강 시작과 레슨 기록을 날짜 내림차순으로 섞는다. 서버는 둘을 따로 주고 섞는 것은 화면이 한다.
 * renderRecordActions: 기록 아래에 붙일 것 (내가 쓴 기록의 수정·삭제 등)
 */
export function Timeline({ enrollments, records, renderRecordActions }: {
  enrollments: EnrollmentDetail[]
  records: LessonRecord[]
  renderRecordActions?: (record: LessonRecord) => ReactNode
}) {
  const subjectOf = new Map(enrollments.map((e) => [e.id, e.subjectName]))
  const items: Item[] = [
    ...enrollments.map((e): Item => ({ kind: 'start', date: e.startsOn, enrollment: e })),
    ...records.map((r): Item => ({ kind: 'record', date: r.lessonDate, record: r })),
  ].sort((a, b) => b.date.localeCompare(a.date) || (a.kind === 'record' ? -1 : 1))

  if (items.length === 0) return <p className="card empty">아직 기록이 없어요.</p>

  return (
    <ol className="timeline">
      {items.map((item) => item.kind === 'start' ? (
        <li key={`e${item.enrollment.id}`} className="timeline-item timeline-event">
          <p className="card-meta">{dateLabel(item.date)}</p>
          <p>{item.enrollment.subjectName} 수강 시작 · {item.enrollment.productName} · {item.enrollment.teacherName}</p>
        </li>
      ) : (
        <li key={`r${item.record.id}`} className="timeline-item card">
          <p className="card-meta">
            {dateLabel(item.date)} · {subjectOf.get(item.record.enrollmentId)} · {item.record.authorName ?? '나'}
            {!item.record.visibleToStudent && <span className="badge">학생 비공개</span>}
          </p>
          {item.record.progress && <p><strong>진도</strong> {item.record.progress}</p>}
          {item.record.homework && <p><strong>숙제</strong> {item.record.homework}</p>}
          {item.record.memo && <p><strong>메모</strong> {item.record.memo}</p>}
          {renderRecordActions?.(item.record)}
        </li>
      ))}
    </ol>
  )
}
