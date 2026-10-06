// 기관 시간대 기준의 날짜·시각. 서버가 주는 ISO 문자열에는 이미 기관 오프셋이 붙어 있어서
// 화면에 보일 때는 문자열을 그대로 자르고(14:00), 서버에 보낼 때만 오프셋을 붙인다.

/** 기관 시간대. 응답에 없으면 브라우저 시간대를 쓴다 */
export const zoneOf = (timezone?: string) => timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone

/** 그 시간대의 오늘 (YYYY-MM-DD) */
export const todayIn = (tz: string) => new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(new Date())

/** 그 시간대의 지금 (HH:mm) */
export const nowTimeIn = (tz: string) =>
  new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date())

/** 날짜와 시각을 그 시간대의 오프셋이 붙은 ISO로 (2026-10-10T14:00:00+09:00) */
export function zonedIso(date: string, time: string, tz: string): string {
  const part = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'longOffset' })
    .formatToParts(new Date(`${date}T${time}:00Z`))
    .find((p) => p.type === 'timeZoneName')?.value ?? 'GMT'
  const offset = part === 'GMT' ? '+00:00' : part.replace('GMT', '')
  return `${date}T${time}:00${offset}`
}

export const hhmm = (iso: string) => iso.slice(11, 16)

export const minutesOf = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5))

export const timeOf = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`

/** 2026-10-10 → 10월 10일 (토) */
export function dateLabel(date: string): string {
  const [y, m, d] = date.split('-').map(Number)
  const day = '일월화수목금토'[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]
  return `${m}월 ${d}일 (${day})`
}
