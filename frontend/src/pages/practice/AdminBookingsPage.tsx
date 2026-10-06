import { useEffect, useState, type FormEvent } from 'react'
import { useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { isManager } from '../../labels'
import { hhmm, todayIn, zoneOf } from '../../practice/time'
import type { Booking } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-25. 날짜별 전체 예약을 방 순서로 보고, 사유를 적어 강제 취소한다 */
export function AdminBookingsPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [date, setDate] = useState('')
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [canceling, setCanceling] = useState<number | null>(null)
  const [message, setMessage] = useState('')
  const [refresh, setRefresh] = useState(0)

  const shownDate = date || (me ? todayIn(zoneOf(me.timezone)) : '')

  useEffect(() => {
    if (!me || !isManager(me.role) || !shownDate) return
    // 날짜를 빨리 바꾸면 이전 날짜의 응답이 늦게 와서 덮어쓸 수 있다. 지난 요청의 응답은 버린다
    let current = true
    api<Booking[]>(`/organizations/${orgId}/practice/bookings?date=${shownDate}`)
      .then((list) => current && setBookings(list))
      .catch((error) => current && setMessage(errorMessage(error)))
    return () => { current = false }
  }, [me, orgId, shownDate, refresh])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  async function forceCancel(b: Booking, event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const reason = String(new FormData(event.currentTarget).get('reason') ?? '').trim()
    setMessage('')
    try {
      await api(`/organizations/${orgId}/practice/bookings/${b.id}/cancel`, { method: 'POST', body: { reason } })
      setCanceling(null)
      setRefresh((v) => v + 1)
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  const active = bookings?.filter((b) => !b.canceledAt) ?? []
  const canceled = bookings?.filter((b) => b.canceledAt) ?? []

  return (
    <main className="page">
      <h1>예약 현황</h1>
      <div className="form-row">
        <input type="date" aria-label="날짜" value={shownDate} onChange={(e) => { setDate(e.target.value); setCanceling(null) }} />
        <span className="card-meta">예약 {active.length}건{canceled.length > 0 && ` · 취소 ${canceled.length}건`}</span>
      </div>
      {message && <p className="alert" role="alert">{message}</p>}
      {bookings && active.length === 0 && <p className="card empty">이 날은 예약이 없어요.</p>}

      <ul className="list section">
        {[...active, ...canceled].map((b) => (
          <li key={b.id} className={b.canceledAt ? 'card card-muted' : 'card'}>
            <p className="card-title">
              {b.roomName} <span className="tnum">{hhmm(b.startsAt)}~{hhmm(b.endsAt)}</span>
              {b.canceledAt && <span className="badge">취소됨</span>}
            </p>
            <p className="card-meta">{b.memberName}{b.cancelReason && ` · 사유: ${b.cancelReason}`}</p>
            {!b.canceledAt && (canceling === b.id ? (
              <form className="form section" onSubmit={(e) => forceCancel(b, e)}>
                <input name="reason" className="input-readonly" aria-label="취소 사유" placeholder="취소 사유 (학생에게 보여요)" required autoFocus />
                <div className="actions">
                  <button className="button button-danger">강제 취소</button>
                  <button className="button" type="button" onClick={() => setCanceling(null)}>그만두기</button>
                </div>
              </form>
            ) : (
              <div className="actions">
                <button className="button button-danger" onClick={() => setCanceling(b.id)}>취소하기</button>
              </div>
            ))}
          </li>
        ))}
      </ul>
    </main>
  )
}
