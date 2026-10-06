import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { dateLabel, hhmm } from '../../practice/time'
import type { Booking } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-24. 내 예약(오늘부터 30일)을 보고 취소한다. 취소된 예약은 사유와 함께 남는다 (Q7) */
export function MyBookingsPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [loadedAt, setLoadedAt] = useState(0)
  const [message, setMessage] = useState('')

  const load = useCallback(() => api<Booking[]>(`/organizations/${orgId}/practice/bookings`).then((list) => {
    setBookings(list)
    setLoadedAt(Date.now()) // 끝난 예약에는 취소 버튼을 두지 않는다. 기준 시각은 불러온 때
  }), [orgId])

  useEffect(() => {
    if (!me) return
    load().catch((error) => setMessage(errorMessage(error)))
  }, [me, load])

  if (me === undefined) return <main className="page" />
  if (me === null) return <NotMember />

  async function cancel(b: Booking) {
    if (!window.confirm(`${b.roomName} ${dateLabel(b.usageDate)} ${hhmm(b.startsAt)}~${hhmm(b.endsAt)} 예약을 취소할까요?`)) return
    setMessage('')
    try {
      await api(`/organizations/${orgId}/practice/bookings/${b.id}/cancel`, { method: 'POST' })
      await load()
    } catch (error) {
      setMessage(errorMessage(error)) // 마감이 지났으면 서버가 "시작 N분 전까지만…"을 준다
    }
  }

  const ended = (b: Booking) => new Date(b.endsAt).getTime() < loadedAt

  return (
    <main className="page">
      <h1>내 예약</h1>
      {message && <p className="alert" role="alert">{message}</p>}
      {bookings?.length === 0 && (
        <div className="card empty">
          <p>앞으로 30일 동안 예약이 없어요.</p>
          <Link className="button button-primary" to={`/orgs/${orgId}/practice`}>연습실 예약하기</Link>
        </div>
      )}
      {bookings && bookings.length > 0 && (
        <ul className="group">
          {bookings.map((b) => (
            <li key={b.id} className={b.canceledAt || ended(b) ? 'row row-muted' : 'row'}>
              <span className="date-block tnum" aria-hidden="true">
                <span>{Number(b.usageDate.slice(5, 7))}월</span>
                <strong>{Number(b.usageDate.slice(8, 10))}</strong>
              </span>
              <span className="row-text">
                <span className="row-title">{b.roomName}{b.canceledAt && <span className="badge">취소됨</span>}</span>
                <span className="row-meta tnum">{dateLabel(b.usageDate)} {hhmm(b.startsAt)}~{hhmm(b.endsAt)}</span>
                {b.cancelReason && <span className="row-meta">관리자 취소 사유: {b.cancelReason}</span>}
              </span>
              {!b.canceledAt && !ended(b) && (
                <button className="link-button" onClick={() => cancel(b)}>취소</button>
              )}
            </li>
          ))}
        </ul>
      )}
      <p className="helper"><Link to={`/orgs/${orgId}/practice`}>연습실 지도로</Link></p>
    </main>
  )
}
