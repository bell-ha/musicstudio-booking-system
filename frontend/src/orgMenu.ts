import {
  BookOpen, CalendarCheck, CalendarDays, ClipboardList, DoorOpen, GraduationCap, KeyRound, Link2, Map, NotebookPen,
  Megaphone, Palette, Settings, Wallet, PencilRuler, SlidersHorizontal, Tags, Users, type LucideIcon,
} from 'lucide-react'
import { isManager, type MyOrganization } from './labels'

export type MenuItem = { to: string; title: string; meta: string; icon: LucideIcon; tab?: string }
export type MenuGroup = { title: string; items: MenuItem[] }

/**
 * 역할과 켠 모듈에 따른 메뉴. 왼쪽 메뉴, 휴대폰 탭 바, 기관 홈이 같은 목록을 쓴다.
 * tab이 있는 항목만 휴대폰 탭 바에 나온다 (DESIGN 5절, 많아야 4개).
 * 학생은 예약, 관리자는 관리, 강사는 지도 보기만 (MVP에서 예약은 학생만, Q2).
 * 관리자도 직접 가르칠 수 있어서(1인 학원) 담당 학생을 함께 둔다.
 */
export function orgMenu(org: MyOrganization): MenuGroup[] {
  const practice = org.modules.includes('PRACTICE_ROOM')
  const academy = org.modules.includes('ACADEMY')
  const groups: MenuGroup[] = []

  // 공지는 모든 기관, 모든 역할 (기관 사이트, FR-SITE-03)
  const news: MenuGroup = { title: '소식', items: [
    { to: 'notices', title: '공지', meta: '학원 소식과 안내', icon: Megaphone },
  ] }

  if (org.role === 'STUDENT') {
    groups.push(news)
    if (practice) groups.push({ title: '연습실', items: [
      { to: 'practice', title: '연습실 예약', meta: '지도에서 빈 방을 찾아 예약해요', icon: Map, tab: '예약' },
      { to: 'practice/my', title: '내 예약', meta: '예약을 보고 취소해요', icon: CalendarCheck, tab: '내 예약' },
    ] })
    if (academy) groups.push({ title: '학원', items: [
      { to: 'academy/me', title: '내 수강', meta: '수강 정보와 레슨 기록', icon: BookOpen, tab: '내 수강' },
    ] })
    return groups
  }

  const manager = isManager(org.role)
  groups.push(news)
  if (academy) groups.push({ title: '학원', items: [
    ...(manager ? [
      { to: 'academy/students', title: '원생', meta: '원생 등록, 수강 관리, 만료 임박', icon: GraduationCap, tab: '원생' },
      { to: 'academy/catalog', title: '과목·상품', meta: '과목과 수업 상품, 가격', icon: Tags },
      ...(org.modules.includes('BILLING') ? [{ to: 'billing', title: '수납', meta: '청구서, 입금·환불, 미납, 영수증', icon: Wallet }] : []),
    ] : []),
    { to: 'academy/schedule', title: '레슨 일정', meta: manager ? '주간 일정, 출결, 그날 전체 휴강' : '내 주간 일정과 출결', icon: CalendarDays, tab: manager ? undefined : '일정' },
    { to: 'academy/my-students', title: '담당 학생', meta: '맡은 학생과 레슨 기록', icon: NotebookPen, tab: manager ? undefined : '담당 학생' },
  ] })
  if (practice) groups.push({ title: '연습실', items: [
    { to: 'practice', title: '연습실 지도', meta: '날짜·시각별로 빈 방을 봐요', icon: Map, tab: '지도' },
    ...(manager ? [
      { to: 'practice/bookings', title: '예약 현황', meta: '날짜별 전체 예약, 사유를 적어 강제 취소', icon: ClipboardList, tab: '예약 현황' },
      { to: 'practice/floors', title: '평면도 편집', meta: '층을 만들고 벽·복도를 그리고 방을 놓아요', icon: PencilRuler },
      { to: 'practice/rooms', title: '방 관리', meta: '방 이름, 수용 인원, 장비, 점검 중 표시', icon: DoorOpen },
      { to: 'practice/policy', title: '예약 정책', meta: '운영 시간, 시간 단위, 이용 한도, 예약 오픈', icon: SlidersHorizontal },
    ] : []),
  ] })
  if (manager) groups.push({ title: '기관 관리', items: [
    ...(org.role === 'OWNER' ? [{ to: 'settings', title: '기관 설정', meta: '이름, 쓰는 기능(연습실·학원 관리)', icon: Settings }] : []),
    { to: 'site', title: '사이트 꾸미기', meta: '로고, 소개, 연락처, 기관 색, 공개 페이지', icon: Palette },
    { to: 'members', title: '멤버', meta: '가입 신청 승인, 역할 변경, 비활성화', icon: Users },
    { to: 'invite', title: '초대 링크', meta: '역할을 정해 한 번 쓰는 링크를 만들어요', icon: Link2 },
    { to: 'join-code', title: '가입 코드', meta: '코드를 받은 사람이 신청하면 승인해요', icon: KeyRound },
  ] })
  return groups
}
