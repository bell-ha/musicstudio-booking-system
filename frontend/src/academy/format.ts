import type { EnrollmentStatus, Product } from './types'

export const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

export const STATUS_LABEL: Record<EnrollmentStatus, string> = {
  ACTIVE: '수강 중', PAUSED: '일시정지', ENDED: '종료', REFUNDED: '환불',
}

export const productTerms = (p: Product) =>
  `${p.kind === 'PERIOD' ? `${p.periodMonths}개월` : `${p.sessionCount}회`} · ${p.lessonMinutes}분 · ${won(p.price)}`

/** 서버와 같은 공식: 시작일 + N개월 − 1일. 월말은 그 달 마지막 날로 맞춘다 (Java plusMonths) */
export function periodEnd(startsOn: string, months: number): string {
  const [y, m, d] = startsOn.split('-').map(Number)
  const targetMonth = m - 1 + months
  const lastDay = new Date(Date.UTC(y, targetMonth + 1, 0)).getUTCDate()
  const end = new Date(Date.UTC(y, targetMonth, Math.min(d, lastDay)))
  end.setUTCDate(end.getUTCDate() - 1)
  return end.toISOString().slice(0, 10)
}

/** 오늘부터 종료일까지 남은 날 (지났으면 음수) */
export function daysUntil(date: string, today: string): number {
  return Math.round((Date.parse(date) - Date.parse(today)) / 86400000)
}

/** 서버는 숫자만 준다 (01012345678). 가린 값(010-****-5678)은 그대로 둔다 */
export function formatPhone(phone: string | null): string | null {
  if (!phone || !/^\d{10,11}$/.test(phone)) return phone
  return phone.length === 11
    ? `${phone.slice(0, 3)}-${phone.slice(3, 7)}-${phone.slice(7)}`
    : `${phone.slice(0, 3)}-${phone.slice(3, 6)}-${phone.slice(6)}`
}
