import { ApiError } from '../api'
import type { Conflict, Day, SessionStatus, Slot } from './types'

export const DAYS: Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
export const DAY_SHORT: Record<Day, string> = {
  MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목', FRIDAY: '금', SATURDAY: '토', SUNDAY: '일',
}

export const SESSION_LABEL: Record<SessionStatus, string> = {
  SCHEDULED: '예정', ATTENDED: '출석', ABSENT: '결석', EXCUSED: '사전 결석', CANCELED: '휴강',
}

/** "화·목 16:00", 시각이 다르면 "화 16:00 · 목 18:00" */
export function slotSummary(slots: Slot[]): string {
  if (slots.length === 0) return ''
  const times = new Set(slots.map((s) => s.startTime.slice(0, 5)))
  return times.size === 1
    ? `${slots.map((s) => DAY_SHORT[s.dayOfWeek]).join('·')} ${[...times][0]}`
    : slots.map((s) => `${DAY_SHORT[s.dayOfWeek]} ${s.startTime.slice(0, 5)}`).join(' · ')
}

/** 서버 시각(UTC)을 기관 시간대의 HH:MM으로 */
export function localTime(iso: string, tz: string): string {
  return new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .format(new Date(iso))
}

/** 그 날짜가 든 주의 월요일 (YYYY-MM-DD) */
export function mondayOf(date: string): string {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7))
  return d.toISOString().slice(0, 10)
}

export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** 409 SCHEDULE_CONFLICT의 겹치는 날짜 목록을 한 줄로 */
export function conflictText(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.problem.code !== 'SCHEDULE_CONFLICT') return null
  const p = error.problem as unknown as { conflicts?: Conflict[]; conflictCount?: number }
  const list = p.conflicts ?? []
  if (list.length === 0) return '강사나 원생의 다른 레슨과 시간이 겹쳐요.'
  const dates = list.slice(0, 5).map((c) => `${c.date.slice(5).replace('-', '/')}(${c.with === 'TEACHER' ? '강사' : '원생'})`)
  const more = (p.conflictCount ?? list.length) - dates.length
  return `다른 레슨과 겹쳐요: ${dates.join(', ')}${more > 0 ? ` 외 ${more}건` : ''}`
}
