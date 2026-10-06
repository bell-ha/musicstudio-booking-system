// 학원 관리 API 계약 (02-API 24~36). 날짜는 YYYY-MM-DD, 금액은 원 단위 정수.

export type ProductKind = 'COUNT' | 'PERIOD'
export type EnrollmentStatus = 'ACTIVE' | 'PAUSED' | 'ENDED' | 'REFUNDED'

export type Subject = { id: number; name: string }
export type Product = {
  id: number
  subjectId: number
  name: string
  kind: ProductKind
  sessionCount: number | null
  periodMonths: number | null
  lessonMinutes: number
  price: number
}
export type Catalog = { subjects: Subject[]; products: Product[] }

export type StudentSummary = {
  id: number
  name: string
  birthYear: number | null
  phone: string | null
  active: boolean
  linked: boolean
  enrollments: {
    id: number
    subjectId: number
    subjectName: string
    productName: string
    teacherMembershipId: number
    teacherName: string
    status: EnrollmentStatus
    endsOn: string | null
    totalSessions: number | null
  }[]
}

export type StudentInfo = {
  id: number
  name: string
  birthYear: number | null
  phone: string | null
  guardianName: string | null
  guardianPhone: string | null
  memo: string | null
  active: boolean
  membershipId: number | null
}

export type EnrollmentDetail = {
  id: number
  productName: string
  subjectName: string
  kind: ProductKind
  teacherMembershipId: number
  teacherName: string
  status: EnrollmentStatus
  price: number | null
  startsOn: string
  endsOn: string | null
  totalSessions: number | null
  pausedAt: string | null
  version: number
}

export type LessonRecord = {
  id: number
  enrollmentId: number
  authorMembershipId: number
  authorName: string | null
  lessonDate: string
  progress: string | null
  homework: string | null
  memo: string | null
  visibleToStudent: boolean
  mine: boolean
}

export type StudentDetail = { student: StudentInfo; enrollments: EnrollmentDetail[]; records: LessonRecord[] }
