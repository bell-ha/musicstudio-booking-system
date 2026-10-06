import { useEffect, useRef, useState } from 'react'
import { api, ApiError, errorMessage } from '../api'
import { dateLabel, hhmm } from './time'
import type { Booking, Policy, Slot, Timetable } from './types'

type Props = {
  orgId: string
  room: { id: number; name: string }
  date: string
  policy: Policy
  canBook: boolean
  onClose: () => void
  onBooked: (booking: Booking) => void
}

const STATUS_TEXT = { AVAILABLE: '비어 있음', BOOKED: '예약됨', UNAVAILABLE: '사용 불가' } as const

/** 예약이 실패한 이유를 화면 문구로. 숫자나 시각이 필요한 것만 직접 만들고 나머지는 서버 문구를 쓴다 */
function bookingError(error: unknown): string {
  if (!(error instanceof ApiError)) return errorMessage(error)
  const p = error.problem as Record<string, unknown>
  switch (p.code) {
    case 'ROOM_OVERLAP': return '방금 다른 사람이 이 시간을 예약했어요. 시간표를 새로 불러왔어요.'
    case 'PERSON_OVERLAP': {
      const c = p.conflictingBooking as { startsAt: string; endsAt: string } | undefined
      return c ? `같은 시간(${hhmm(c.startsAt)}~${hhmm(c.endsAt)})에 이미 다른 방을 예약했어요.` : errorMessage(error)
    }
    case 'DAILY_LIMIT_EXCEEDED':
      return `하루 최대 ${p.limitMinutes}분까지 예약할 수 있어요. 그날 이미 ${p.usedMinutes}분을 예약했어요.`
    case 'NOT_OPEN_YET':
      return `아직 예약이 열리지 않았어요. ${new Date(String(p.opensAt)).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })}에 열려요.`
    default: return errorMessage(error)
  }
}

/** 방 시간표. 휴대폰에서는 아래 시트, 넓은 화면에서는 오른쪽 패널 (CSS만 다르다). <dialog>가 배경·Esc·포커스를 처리한다 */
export function TimetableSheet({ orgId, room, date, policy, canBook, onClose, onBooked }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [timetable, setTimetable] = useState<Timetable | null>(null)
  const [first, setFirst] = useState<number | null>(null)
  const [last, setLast] = useState<number | null>(null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    dialog.current?.showModal()
  }, [])

  useEffect(() => {
    let current = true
    api<Timetable>(`/organizations/${orgId}/practice/rooms/${room.id}/timetable?date=${date}`)
      .then((t) => {
        if (!current) return
        setTimetable(t)
        setFirst(null)
        setLast(null)
      })
      .catch((error) => current && setMessage(errorMessage(error)))
    return () => { current = false }
  }, [orgId, room.id, date, version])

  const slots = timetable?.slots ?? []
  const maxSlots = policy.maxContinuousMinutes / policy.slotMinutes

  function pick(index: number) {
    setMessage('')
    if (slots[index].status !== 'AVAILABLE') return
    // 처음 누르면 시작, 다음에 누르면 끝. 사이에 빈 칸이 아닌 칸이 있거나 너무 길면 거기서 다시 시작한다
    if (first === null || last !== first || index < first) {
      setFirst(index)
      setLast(index)
      return
    }
    const range = slots.slice(first, index + 1)
    if (range.some((s) => s.status !== 'AVAILABLE')) {
      setMessage('사이에 예약할 수 없는 시간이 있어요.')
      setFirst(index)
      setLast(index)
    } else if (range.length > maxSlots) {
      setMessage(`한 번에 ${policy.maxContinuousMinutes}분까지 예약할 수 있어요.`)
    } else {
      setLast(index)
    }
  }

  async function book() {
    if (first === null || last === null) return
    setSubmitting(true)
    setMessage('')
    try {
      const booking = await api<Booking>(`/organizations/${orgId}/practice/bookings`, {
        method: 'POST',
        body: { roomId: room.id, startsAt: slots[first].start, endsAt: slots[last].end },
      })
      onBooked(booking)
    } catch (error) {
      setMessage(bookingError(error))
      if (error instanceof ApiError && error.problem.status === 409) setVersion((v) => v + 1)
    } finally {
      setSubmitting(false)
    }
  }

  const selected = (i: number) => first !== null && last !== null && i >= first && i <= last
  const slotClass = (s: Slot, i: number) =>
    selected(i) ? 'slot slot-selected' : s.mine ? 'slot slot-mine' : `slot slot-${s.status.toLowerCase()}`

  return (
    <dialog ref={dialog} className="sheet" onClose={onClose} onClick={(e) => e.target === dialog.current && dialog.current?.close()}>
      <header className="sheet-header">
        <div>
          <p className="card-title">{room.name}</p>
          <p className="card-meta">{dateLabel(date)}</p>
        </div>
        <button className="button" onClick={() => dialog.current?.close()}>닫기</button>
      </header>

      {timetable?.closed && <p className="card empty">이 날은 운영하지 않아요.</p>}
      <ul className="slots">
        {slots.map((s, i) => (
          <li key={s.start}>
            <button type="button" className={slotClass(s, i)} disabled={!canBook || s.status !== 'AVAILABLE'}
              aria-pressed={selected(i)} onClick={() => pick(i)}>
              <span className="slot-time">{hhmm(s.start)}</span>
              <span>{selected(i) ? '선택함' : s.mine ? '내 예약' : STATUS_TEXT[s.status]}</span>
            </button>
          </li>
        ))}
      </ul>

      <footer className="sheet-footer">
        {message && <p className="alert" role="alert">{message}</p>}
        {!canBook && <p className="card-meta">연습실 예약은 학생만 할 수 있어요.</p>}
        {canBook && (
          <button className="button button-primary button-block" disabled={first === null || submitting} onClick={book}>
            {first !== null && last !== null
              ? `${hhmm(slots[first].start)}~${hhmm(slots[last].end)} 예약하기`
              : '시작 시간을 골라 주세요'}
          </button>
        )}
      </footer>
    </dialog>
  )
}
