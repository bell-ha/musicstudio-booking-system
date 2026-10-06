export type OrgType = 'ACADEMY' | 'SCHOOL' | 'OTHER'
export type Role = 'OWNER' | 'MANAGER' | 'TEACHER' | 'STUDENT'
export type MemberStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'REJECTED'

export const TYPE_LABEL: Record<OrgType, string> = { ACADEMY: '학원', SCHOOL: '학교', OTHER: '기타' }

export const ROLE_LABEL: Record<Role, string> = { OWNER: '소유자', MANAGER: '관리자', TEACHER: '강사', STUDENT: '학생' }

/** OWNER ⊃ MANAGER */
export const isManager = (role: Role) => role === 'OWNER' || role === 'MANAGER'

export type MyOrganization = {
  organizationId: number
  name: string
  type: OrgType
  role: Role
  status: 'PENDING' | 'ACTIVE' // 서버는 거절·비활성 멤버십을 주지 않는다
  modules: string[]
}
