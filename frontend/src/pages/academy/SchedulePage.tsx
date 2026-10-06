import { useCallback, useEffect, useState } from 'react'
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
  const [week, setWeek] = useState(() => mondayOf(todayIn(tz)))
  const [sessions, setSessions] = useState<Session[]>()
  const [unmarked, setUnmarked] = useState<Session[]>([])
  const [open, setOpen] = useState<Session | null>(null)
  const [now, setNow] = useState(0)
  const [notice, setNotice] = useState('')
  const [message, setMessage] = useState('')

  const load = useCallback(() => {
    const base = `/organizations/${orgId}/academy/sessions`
    api<Session[]>(`${base}?from=${week}&to=${addDays(week, 6)}`).then((list) => { setNow(Date.now()); setSessions(list) }).catch((e) => setMessage(errorMessage(e)))
    api<Session[]>(`${base}?unmarked=true`).then(setUnmarked).catch(() => setUnmarked([]))
  }, [orgId, week])

  useEffect(() => {
    if (me && me.role !== 'STUDENT') load()
  }, [me, load])

  if (me === undefined) return <main className="page page-wide" />
  if (me === null || me.role === 'STUDENT') return <NotMember />
  const manager = isManager(me.role)
  const today = todayIn(tz)

  async function cancelDay(date: string) {
    const reason = window.prompt(`${dateLabel(date)} 레슨을 모두 휴강할까요? 사유를 적어 주세요 (예: 추석 연휴)`)
    if (!reason?.trim()) return
    try {
      const r = await api<{ canceled: number; appended: number; skipped: number }>(
        `/organizations/${orgId}/academy/sessions/cancel-day`, { method: 'POST', body: { date, reason } })
      setNotice(`${r.canceled}개 회차를 휴강했어요.${r.appended > 0 ? ` 횟수권은 ${r.appended}회를 뒤에 이어 붙였어요.` : ''}`
        + (r.skipped > 0 ? ` 그사이 새로 생긴 회차 ${r.skipped}개는 남았어요. 한 번 더 눌러 주세요.` : ''))
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
                {manager && open.length > 0 && date >= today && (
                  <button type="button" className="link-button" onClick={() => cancelDay(date)}>이날 휴강</button>
                )}
              </div>
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
