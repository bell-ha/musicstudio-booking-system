import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api'
import { dateLabel } from '../practice/time'
import { localTime, SESSION_LABEL } from './lessons'
import type { Session, SessionStatus } from './types'

const MARKS: SessionStatus[] = ['ATTENDED', 'ABSENT', 'EXCUSED']

/**
 * UC-51 출결 시트. 출석·결석은 레슨이 시작한 뒤에만, 사전 결석은 언제나 (서버도 같은 규칙, 422 TOO_EARLY).
 * 관리자에게는 휴강(사유 필수)을 위험 행으로 둔다.
 */
export function AttendanceSheet({ orgId, session, tz, manager, onDone, onClose }: {
  orgId: string
  session: Session
  tz: string
  manager: boolean
  onDone: (notice: string) => void
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [started] = useState(() => Date.parse(session.startsAt) <= Date.now()) // 시트를 연 시각 기준
  const [status, setStatus] = useState<SessionStatus>(
    session.status === 'SCHEDULED' ? (started ? 'ATTENDED' : 'EXCUSED') : session.status)
  const [note, setNote] = useState(session.status === 'CANCELED' ? '' : session.note ?? '')
  const [canceling, setCanceling] = useState(false)
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    dialog.current?.showModal()
  }, [])

  async function save() {
    setMessage('')
    try {
      const r = await api<{ shortfall: number }>(`/organizations/${orgId}/academy/sessions/${session.id}/attendance`, {
        method: 'PUT', body: { status, note: note.trim() || null },
      })
      onDone(r.shortfall > 0
        ? `저장했어요. 이어 붙일 자리를 찾지 못한 회차가 ${r.shortfall}개 있어요. 보강으로 넣어 주세요.`
        : status === 'EXCUSED' ? '저장했어요. 사전 결석이라 횟수에서 빼지 않아요.' : '저장했어요.')
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  async function cancel() {
    if (!reason.trim()) {
      setMessage('휴강 사유를 적어 주세요.')
      return
    }
    try {
      await api(`/organizations/${orgId}/academy/sessions/${session.id}/cancel`, { method: 'POST', body: { reason } })
      onDone('휴강했어요.')
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  const canceled = session.status === 'CANCELED'

  return (
    <dialog ref={dialog} className="sheet" onClose={onClose} onClick={(e) => e.target === dialog.current && dialog.current?.close()}>
      <div className="sheet-form">
        <header className="sheet-header">
          <div>
            <p className="card-title">{session.studentName} · {session.subjectName}</p>
            <p className="card-meta tnum">
              {dateLabel(session.localDate)} {localTime(session.startsAt, tz)}~{localTime(session.endsAt, tz)}
              {session.kind === 'MAKEUP' && ' · 보강'}
            </p>
          </div>
          <button type="button" className="shell-link" aria-label="닫기" onClick={() => dialog.current?.close()}><X size={20} /></button>
        </header>
        <div className="sheet-body form">
          {canceled ? (
            <p className="notice-inline">휴강한 회차예요. 사유: {session.note}</p>
          ) : (
            <>
              <fieldset className="segmented" aria-label="출결">
                {MARKS.map((m) => {
                  const disabled = m !== 'EXCUSED' && !started
                  return (
                    <label key={m} className={status === m ? 'segment segment-on' : 'segment'} aria-disabled={disabled}
                      style={disabled ? { opacity: 0.4 } : undefined}>
                      <input type="radio" name="status" checked={status === m} disabled={disabled} onChange={() => setStatus(m)} />
                      {SESSION_LABEL[m]}
                    </label>
                  )
                })}
              </fieldset>
              {!started && <p className="hint">레슨 시작 전이라 사전 결석만 남길 수 있어요.</p>}
              <div className="field">
                <label htmlFor="att-note">메모 (선택)</label>
                <input id="att-note" value={note} maxLength={500} placeholder="예: 10분 지각" onChange={(e) => setNote(e.target.value)} />
              </div>
              <Link className="row row-compact" to={`/orgs/${orgId}/academy/students/${session.studentId}`}>
                <span className="row-text"><span className="row-title-plain">레슨 기록 쓰기 · 원생 보기</span></span>
              </Link>
              {manager && (canceling ? (
                <div className="field">
                  <label htmlFor="cancel-reason">휴강 사유</label>
                  <input id="cancel-reason" value={reason} maxLength={500} placeholder="예: 강사 병가" onChange={(e) => setReason(e.target.value)} />
                  <div className="actions">
                    <button type="button" className="button button-danger" onClick={cancel}>휴강하기</button>
                    <button type="button" className="button" onClick={() => setCanceling(false)}>취소</button>
                  </div>
                </div>
              ) : (
                <button type="button" className="button button-danger" onClick={() => setCanceling(true)}>이 회차 휴강</button>
              ))}
            </>
          )}
          {message && <p className="alert" role="alert">{message}</p>}
        </div>
        {!canceled && (
          <footer className="sheet-footer">
            <button type="button" className="button button-primary" onClick={save}>저장</button>
          </footer>
        )}
      </div>
    </dialog>
  )
}
