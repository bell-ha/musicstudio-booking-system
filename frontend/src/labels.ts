export type OrgType = 'ACADEMY' | 'SCHOOL' | 'OTHER'

export const TYPE_LABEL: Record<OrgType, string> = { ACADEMY: '학원', SCHOOL: '학교', OTHER: '기타' }

export const ROLE_LABEL = { OWNER: '소유자', MANAGER: '관리자', TEACHER: '강사', STUDENT: '학생' } as const
