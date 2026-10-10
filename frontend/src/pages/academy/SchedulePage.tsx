import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { AttendanceSheet } from '../../academy/AttendanceSheet'
import { addDays, DAY_SHORT, DAYS, mondayOf } from '../../academy/lessons'
import { SessionRow } from '../../academy/SessionRow'
import type { Session } from '../../academy/types'
import { isManager } from '../../labels'
import { dateLabel, todayIn, zoneOf } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/**
 * UC-50 주간 레슨 일정. 요일별 묶음 목록(휴대폰·컴퓨터 같은 모양). 관리자는 기관 전체, 강사는 자기 회차.
 * 관리자는 날짜 머리에서 그날 전체 휴강(UC-52, 명절).
 */
export function SchedulePage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const tz = zoneOf(me?.timezone)
  // 고른 주가 없으면 기관 시간대의 이번 주. 첫 렌더에는 기관 정보가 없어서 초기값으로 계산하면 브라우저 시간대가 된다 (리뷰 29 1-2)
  const [picked, setPicked] = useState<string | null>(null)
  const week = picked ?? mondayOf(todayIn(tz))
  const setWeek = setPicked
  const [refresh, setRefresh] = useState(0)
  const [cancelingDay, setCancelingDay] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [sessions, setSessions] = useState<Session[]>()
  const [unmarked, setUnmarked] = useState<Session[]>([])
  const [open, setOpen] = useState<Session | null>(null)
  const [now, setNow] = useState(0)
  const [notice, setNotice] = useState('')
  const [message, setMessage] = useState('')

  const load = () => setRefresh((v) => v + 1)

  // 주를 빨리 넘기면 늦게 온 지난 주의 응답이 덮지 않게 한다 (리뷰 29 1-1)
  useEffect(() => {
    if (!me || me.role === 'STUDENT') return
    let current = true
    const base = `/organizations/${orgId}/academy/sessions`
    api<Session[]>(`${base}?from=${week}&to=${addDays(week, 6)}`)
      .then((list) => { if (current) { setNow(Date.now()); setSessions(list) } })
      .catch((e) => current && setMessage(errorMessage(e)))
    api<Session[]>(`${base}?unmarked=true`).then((list) => current && setUnmarked(list)).catch(() => current && setUnmarked([]))
    return () => { current = false }
  }, [me, orgId, week, refresh])

  if (me === undefined) return <main className="page page-wide" />
  if (me === null || me.role === 'STUDENT') return <NotMember />
  const manager = isManager(me.role)
  const today = todayIn(tz)

  async function cancelDay(date: string) {
    if (!reason.trim()) {
      setMessage('휴강 사유를 적어 주세요.')
      return
    }
    try {
      const r = await api<{ canceled: number; appended: number; skipped: number }>(
        `/organizations/${orgId}/academy/sessions/cancel-day`, { method: 'POST', body: { date, reason } })
      setNotice(`${r.canceled}개 회차를 휴강했어요.${r.appended > 0 ? ` 횟수권은 ${r.appended}회를 뒤에 이어 붙였어요.` : ''}`
        + (r.skipped > 0 ? ` 그사이 새로 생긴 회차 ${r.skipped}개는 남았어요. 한 번 더 눌러 주세요.` : ''))
      setCancelingDay(null)
      setReason('')
      load()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  const days = DAYS.map((d, i) => ({ day: d, date: addDays(week, i) }))
  const inactive = (sessions ?? []).filter((s) => !s.teacherActive && s.status === 'SCHEDULED').length

  return (
    <main className="page page-wide">
      <div className="app-bar">
        <h1>레슨 일정</h1>
        <div className="week-nav">
          <button type="button" className="shell-link" aria-label="이전 주" onClick={() => setWeek(addDays(week, -7))}><ChevronLeft size={20} /></button>
          <button type="button" className="button" onClick={() => setWeek(mondayOf(today))}>이번 주</button>
          <button type="button" className="shell-link" aria-label="다음 주" onClick={() => setWeek(addDays(week, 7))}><ChevronRight size={20} /></button>
        </div>
      </div>
      <p className="page-sub tnum">{dateLabel(week)} ~ {dateLabel(addDays(week, 6))}</p>

      {notice && <p className="notice-inline" role="status">{notice}</p>}
      {message && <p className="alert" role="alert">{message}</p>}
      {unmarked.length > 0 && (
        <section>
          <h2 className="group-title">출결을 안 남긴 지난 레슨 {unmarked.length}건</h2>
          <ul className="group">
            {unmarked.map((s) => (
              <li key={s.id}><SessionRow session={s} tz={tz} now={now} showTeacher={manager} onOpen={() => setOpen(s)} /></li>
            ))}
          </ul>
        </section>
      )}
      {sessions?.length === 0 && (
        <p className="notice-inline">{manager
          ? '이 주에는 레슨이 없어요. 레슨은 원생 화면의 수강 카드에서 고정 일정을 정하면 생겨요.'
          : '이 주에는 레슨이 없어요. 수강에 고정 일정이 정해지면 여기에 보여요.'}</p>
      )}
      {manager && inactive > 0 && (
        <p className="notice-inline">비활성 강사의 레슨이 {inactive}개 있어요. 원생 화면에서 강사를 바꿔 주세요.</p>
      )}

      <div className="groups">
        {days.map(({ day, date }) => {
          const list = (sessions ?? []).filter((s) => s.localDate === date)
          const open = list.filter((s) => s.status === 'SCHEDULED')
          return (
            <section key={date}>
              <div className="day-head">
                <h2 className={date === today ? 'group-title day-today' : 'group-title'}>
                  {DAY_SHORT[day]} {date.slice(5).replace('-', '/')}{date === today && ' · 오늘'}
                </h2>
                {manager && open.length > 0 && date >= today && cancelingDay !== date && (
                  <button type="button" className="link-button" onClick={() => { setCancelingDay(date); setReason('') }}>이날 휴강</button>
                )}
              </div>
              {cancelingDay === date && (
                <div className="card-inset form-row wrap">
                  <input aria-label={`${dateLabel(date)} 휴강 사유`} placeholder="사유 (예: 추석 연휴)" value={reason} maxLength={500}
                    onChange={(e) => setReason(e.target.value)} />
                  <button type="button" className="button button-danger" onClick={() => cancelDay(date)}>{open.length}개 휴강</button>
                  <button type="button" className="button" onClick={() => setCancelingDay(null)}>취소</button>
                </div>
              )}
              <ul className="group">
                {list.length === 0 && <li className="row row-compact"><span className="row-meta">레슨 없음</span></li>}
                {list.map((s) => (
                  <li key={s.id}><SessionRow session={s} tz={tz} now={now} showTeacher={manager} onOpen={() => setOpen(s)} /></li>
                ))}
              </ul>
            </section>
          )
        })}
      </div>

      {open && (
        <AttendanceSheet orgId={orgId!} session={open} tz={tz} manager={manager}
          onDone={(n) => { setOpen(null); setNotice(n); load() }} onClose={() => setOpen(null)} />
      )}
    </main>
  )
}
