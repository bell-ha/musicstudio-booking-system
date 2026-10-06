import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { cropToContent } from '../../practice/crop'
import { FloorGrid } from '../../practice/FloorGrid'
import { dateLabel, minutesOf, nowTimeIn, timeOf, todayIn, zonedIso, zoneOf } from '../../practice/time'
import { TimetableSheet } from '../../practice/TimetableSheet'
import { DAYS, type Availability, type FloorsResponse, type Placement, type Policy } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

const ROOM_TEXT = { AVAILABLE: '비어 있음', BOOKED: '예약됨', UNAVAILABLE: '사용 불가' } as const

/** 그날 운영 시간의 칸 시작 시각들. 휴무면 [] */
function slotTimes(policy: Policy, date: string): string[] {
  const [y, m, d] = date.split('-').map(Number)
  const day = DAYS[(new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7]
  const hours = policy.hours[day]
  if (!hours) return []
  const times = []
  for (let t = minutesOf(hours[0]); t < minutesOf(hours[1]); t += policy.slotMinutes) times.push(timeOf(t))
  return times
}

/** UC-23. 층 평면도에서 날짜·시각을 고르면 방마다 빈 방 여부를 보여 주고, 방을 누르면 시간표에서 예약한다 */
export function PracticeMapPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const tz = zoneOf(me?.timezone)
  const [data, setData] = useState<FloorsResponse | null>(null)
  const [floorId, setFloorId] = useState<number | null>(null)
  // 비어 있으면 기본값(오늘, 지금 다음 칸)을 쓴다. 기본값은 렌더링 중에 계산한다
  const [pickedDate, setDate] = useState('')
  const [pickedTime, setTime] = useState('')
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [openRoom, setOpenRoom] = useState<Placement | null>(null)
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')
  const [refresh, setRefresh] = useState(0)

  const loadFloors = useCallback(() => api<FloorsResponse>(`/organizations/${orgId}/practice/floors`).then((loaded) => {
    setData(loaded)
    setFloorId((current) => loaded.floors.find((f) => f.id === current)?.id ?? loaded.floors[0]?.id ?? null)
  }), [orgId])

  useEffect(() => {
    if (!me) return
    loadFloors().catch((error) => setMessage(errorMessage(error)))
  }, [me, loadFloors])

  const date = pickedDate || todayIn(tz)
  const times = data ? slotTimes(data.policy, date) : []
  const nextSlot = pickedDate ? undefined : times.find((t) => minutesOf(t) >= minutesOf(nowTimeIn(tz)))
  const time = pickedTime && times.includes(pickedTime) ? pickedTime : nextSlot ?? times[0] ?? ''

  useEffect(() => {
    if (!floorId || !date || !time) return
    const at = encodeURIComponent(zonedIso(date, time, tz))
    let current = true // 시각을 빨리 바꾸면 지난 요청의 응답이 늦게 와서 덮어쓸 수 있다
    api<Availability>(`/organizations/${orgId}/practice/floors/${floorId}/availability?at=${at}`)
      .then((a) => {
        if (!current) return
        setAvailability(a)
        // 그사이 관리자가 평면도를 바꿨으면 다시 받는다
        if (a.version !== data?.floors.find((f) => f.id === floorId)?.version) loadFloors()
      })
      .catch((error) => current && setMessage(errorMessage(error)))
    return () => { current = false }
  }, [orgId, floorId, date, time, tz, refresh, data, loadFloors])

  if (me === undefined) return <main className="page" />
  if (me === null) return <NotMember />

  const floor = data?.floors.find((f) => f.id === floorId)
  const statusOf = (roomId: number) => availability?.floorId === floorId ? availability.rooms.find((r) => r.roomId === roomId) : undefined

  function changeDate(next: string) {
    setDate(next)
    if (!pickedTime) setTime(time) // 날짜를 바꿔도 보던 시각을 유지한다 (그날 없는 시각이면 첫 칸)
  }

  return (
    <main className="page page-wide">
      <h1>연습실 예약</h1>
      {data?.floors.length === 0 && <p className="card empty">아직 평면도가 없어요. 관리자가 평면도를 만들면 여기에서 예약할 수 있어요.</p>}

      {data && data.floors.length > 1 && (
        <div className="tabs" role="tablist">
          {data.floors.map((f) => (
            <button key={f.id} role="tab" className="tab" aria-selected={f.id === floorId} onClick={() => setFloorId(f.id)}>{f.name}</button>
          ))}
        </div>
      )}

      {floor && (
        <>
          <div className="form-row">
            <input type="date" aria-label="날짜" value={date} min={todayIn(tz)} onChange={(e) => changeDate(e.target.value)} />
            <select aria-label="시각" value={time} onChange={(e) => setTime(e.target.value)} disabled={times.length === 0}>
              {times.length === 0 && <option value="">휴무</option>}
              {times.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          {date && <p className="card-meta">{dateLabel(date)} {time}부터 {data!.policy.slotMinutes}분 동안 비어 있는지 보여요. 방을 누르면 그날 시간표가 나와요.</p>}

          <FloorGrid
            {...cropToContent(floor.layout.width, floor.layout.height, floor.layout.walls, floor.layout.corridors,
              floor.rooms.map((r) => ({ id: r.id, name: r.name, x: r.x!, y: r.y!, w: r.w!, h: r.h! })))}
            renderRoom={(room) => {
              const s = statusOf(room.id)
              const kind = !s ? '' : s.mine ? 'room-mine' : `room-${s.status.toLowerCase()}`
              const text = !s ? '' : s.mine ? '내 예약' : s.reason === 'UNDER_MAINTENANCE' ? '점검 중' : ROOM_TEXT[s.status]
              return {
                className: kind,
                content: <><strong>{room.name}</strong><span>{text}</span></>,
                onClick: () => { setNotice(''); setOpenRoom(room) },
              }
            }}
          />
          {notice && <p className="notice" role="status">{notice}</p>}
        </>
      )}
      {message && <p className="alert" role="alert">{message}</p>}

      {openRoom && data && (
        <TimetableSheet
          orgId={orgId!}
          room={openRoom}
          date={date}
          policy={data.policy}
          canBook={me.role === 'STUDENT'}
          onClose={() => setOpenRoom(null)}
          onBooked={(b) => {
            setOpenRoom(null)
            setNotice(`${openRoom.name} ${b.startsAt.slice(11, 16)}~${b.endsAt.slice(11, 16)} 예약했어요.`)
            setRefresh((v) => v + 1)
          }}
        />
      )}

    </main>
  )
}
