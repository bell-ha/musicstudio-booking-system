import { useCallback, useEffect, useState } from 'react'
import { CalendarDays, ChevronRight } from 'lucide-react'
import { Link } from 'react-router'
import { api } from '../api'
import { todayIn, zoneOf } from '../practice/time'
import type { MyOrganization } from '../labels'
import { isManager } from '../labels'
import { AttendanceSheet } from './AttendanceSheet'
import { SessionRow } from './SessionRow'
import type { Session } from './types'

/**
 * 기관 홈 "오늘 레슨" (UC-51 1). 강사와, 직접 가르치는 관리자에게 자기 회차만.
 * 출결을 안 남긴 지난 레슨이 있으면 위에 n건 행을 둔다(자동 출석 처리는 없다).
 */
export function TodayLessons({ org }: { org: MyOrganization }) {
  const orgId = String(org.organizationId)
  const tz = zoneOf(org.timezone)
  const [today, setToday] = useState<Session[]>([])
  const [unmarked, setUnmarked] = useState<Session[]>([])
  const [open, setOpen] = useState<Session | null>(null)
  const [now, setNow] = useState(0)
  const [notice, setNotice] = useState('')

  const load = useCallback(() => {
    const base = `/organizations/${orgId}/academy/sessions`
    const date = todayIn(tz)
    api<Session[]>(`${base}?from=${date}&to=${date}&mine=true`).then((list) => { setNow(Date.now()); setToday(list) }).catch(() => setToday([]))
    api<Session[]>(`${base}?unmarked=true&mine=true`).then(setUnmarked).catch(() => setUnmarked([]))
  }, [orgId, tz])

  useEffect(load, [load])

  if (today.length === 0 && unmarked.length === 0) return null

  return (
    <section>
      <h2 className="group-title">오늘 레슨 {today.length > 0 && `· ${today.length}개`}</h2>
      {notice && <p className="notice-inline" role="status">{notice}</p>}
      <ul className="group">
        {unmarked.length > 0 && (
          <li>
            <Link className="row row-compact" to="academy/schedule">
              <span className="row-icon row-icon-warn"><CalendarDays size={18} /></span>
              <span className="row-text"><span className="row-title">출결을 안 남긴 레슨 {unmarked.length}건</span></span>
              <ChevronRight className="row-chevron" size={20} />
            </Link>
          </li>
        )}
        {today.map((s) => (
          <li key={s.id}><SessionRow session={s} tz={tz} now={now} showTeacher={false} onOpen={() => setOpen(s)} /></li>
        ))}
      </ul>
      {open && (
        <AttendanceSheet orgId={orgId} session={open} tz={tz} manager={isManager(org.role)}
          onDone={(n) => { setOpen(null); setNotice(n); load() }} onClose={() => setOpen(null)} />
      )}
    </section>
  )
}
