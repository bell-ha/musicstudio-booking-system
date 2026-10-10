import type { OrgType } from './labels'

export const MODULES = [
  { key: 'PRACTICE_ROOM', title: '연습실', meta: '평면도, 방, 예약 정책, 지도에서 예약' },
  { key: 'ACADEMY', title: '학원 관리', meta: '원생, 수강, 레슨 일정·출결, 레슨 기록' },
  { key: 'BILLING', title: '청구·결제', meta: '청구서, 입금·환불 기록, 미납, 영수증 (학원 관리 필요)' },
] as const

/** 기관을 만들 때 처음 켜 둘 기능. 진실은 서버의 OrganizationType.defaultModules이고 이것은 처음 보여 줄 값이다 */
export const DEFAULT_MODULES: Record<OrgType, string[]> = {
  ACADEMY: ['PRACTICE_ROOM', 'ACADEMY'],
  SCHOOL: ['PRACTICE_ROOM'],
  OTHER: ['PRACTICE_ROOM'],
}

/** 하나를 켜거나 끈 결과. 청구는 원생·수강에 붙어서 학원 관리를 끄면 청구·결제도 같이 끈다 (서버는 422) */
export function toggled(modules: string[], key: string, on: boolean): string[] {
  return on ? [...modules, key] : modules.filter((m) => m !== key && !(key === 'ACADEMY' && m === 'BILLING'))
}
