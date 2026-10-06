// 연습실 API 계약 (02-API 13~18). 좌표는 0부터, x는 열, y는 행.

// 요일은 Java DayOfWeek 이름 그대로다. 정책 오류의 field도 hours.MONDAY
export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const
export type Day = (typeof DAYS)[number]
export const DAY_LABEL: Record<Day, string> = {
  MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목', FRIDAY: '금', SATURDAY: '토', SUNDAY: '일',
}

export type Policy = {
  hours: Record<Day, [string, string] | null>
  slotMinutes: 30 | 60
  maxContinuousMinutes: number
  dailyMaxMinutes: number
  // 서버는 쓰지 않는 필드를 null로 준다
  open: { mode: 'ROLLING'; days: number; dayOfWeek?: null; time?: null } | { mode: 'WEEKLY'; dayOfWeek: Day; time: string; days?: null }
  cancelDeadlineMinutes: number
}

export type Room = {
  id: number
  name: string
  capacity: number
  equipment: string[]
  bookable: boolean
  floorId: number | null
  x: number | null
  y: number | null
  w: number | null
  h: number | null
}

export type Cell = [number, number]

export type Layout = { width: number; height: number; walls: Cell[]; corridors: Cell[] }

export type Floor = { id: number; name: string; sortOrder: number; version: number; layout: Layout; rooms: Room[] }

export type FloorsResponse = { policy: Policy; floors: Floor[]; unplacedRooms: Room[] }

// ---------- (B) 지도, 시간표, 예약 (02-API 19~23). 시각은 기관 시간대 오프셋이 붙은 ISO 문자열 ----------

export type SlotStatus = 'AVAILABLE' | 'BOOKED' | 'UNAVAILABLE'

export type Availability = {
  floorId: number
  version: number
  at: string
  rooms: { roomId: number; status: SlotStatus; mine: boolean; reason: string | null }[]
}

export type Slot = { start: string; end: string; status: SlotStatus; mine: boolean; reason: string | null }

export type Timetable = { roomId: number; date: string; closed: boolean; slots: Slot[] }

export type Booking = {
  id: number
  roomId: number
  roomName: string | null
  memberName: string | null
  startsAt: string
  endsAt: string
  usageDate: string
  canceledAt: string | null
  cancelReason: string | null
}

/** 격자 위 방 하나. 편집 중인 배치와 저장된 방을 같은 모양으로 그린다 */
export type Placement = { id: number; name: string; x: number; y: number; w: number; h: number }
