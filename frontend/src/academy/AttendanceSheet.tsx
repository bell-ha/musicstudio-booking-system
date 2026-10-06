import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api'
import { dateLabel } from '../practice/time'
import { localTime, SESSION_LABEL } from './lessons'
import type { Session, SessionStatus } from './types'

const MARKS: SessionStatus[] = ['ATTENDED', 'ABSENT', 'EXCUSED']

/**
 * UC-51 출결 시트. 출석·결석은 레슨이 시작한 뒤에만, 사전 결석은 언제나. 최종 판정은 서버(422 TOO_EARLY)가 한다:
 * 브라우저 시계로 버튼을 막으면 시계가 늦을 때 서버가 받을 출석을 막고, 시트를 연 채 시작 시각이 지나도 풀리지 않는다
 * (교차 리뷰 29 1-3). 아직 출결하지 않은 회차는 아무것도 미리 고르지 않는다(출결 안 한 지난 레슨을 열고 바로 저장하면
 * 결석한 학생이 출석이 되므로). 관리자에게는 휴강(사유 필수)을 위험 행으로 둔다(출결 전 회차만).
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
  const [openedAt] = useState(() => Date.now()) // 안내 문구용. 막는 데는 쓰지 않는다
  const beforeStart = openedAt < Date.parse(session.startsAt)
  const [status, setStatus] = useState<SessionStatus | null>(session.status === 'SCHEDULED' ? null : session.status)
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
                {MARKS.map((m) => (
                  <label key={m} className={status === m ? 'segment segment-on' : 'segment'}>
                    <input type="radio" name="status" checked={status === m} onChange={() => setStatus(m)} />
                    {SESSION_LABEL[m]}
                  </label>
                ))}
              </fieldset>
              {beforeStart && <p className="hint">레슨 시작 전에는 사전 결석만 남길 수 있어요. 출석·결석은 시작한 뒤에 눌러 주세요.</p>}
              <div className="field">
                <label htmlFor="att-note">메모 (선택)</label>
                <input id="att-note" value={note} maxLength={500} placeholder="예: 10분 지각" onChange={(e) => setNote(e.target.value)} />
              </div>
              <Link className="row row-compact" to={`/orgs/${orgId}/academy/students/${session.studentId}`}>
                <span className="row-text"><span className="row-title-plain">레슨 기록 쓰기 · 원생 보기</span></span>
              </Link>
              {manager && session.status === 'SCHEDULED' && (canceling ? (
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
            <button type="button" className="button button-primary" onClick={save} disabled={status === null}>
              {status === null ? '출석·결석·사전 결석을 골라 주세요' : '저장'}
            </button>
          </footer>
        )}
      </div>
    </dialog>
  )
}
