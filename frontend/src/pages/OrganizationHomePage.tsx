import { Link, useParams } from 'react-router'
import { isManager, ROLE_LABEL } from '../labels'
import { useMyOrganization } from '../useMyOrganization'

const BOOKING_MENU = [
  { to: 'practice', title: '연습실 예약', meta: '지도에서 빈 방을 찾아 예약해요' },
  { to: 'practice/my', title: '내 예약', meta: '예약을 보고 취소해요' },
]

const PRACTICE_MENU = [
  { to: 'practice', title: '연습실 지도', meta: '날짜·시각별로 빈 방을 봐요' },
  { to: 'practice/bookings', title: '예약 현황', meta: '날짜별 전체 예약, 사유를 적어 강제 취소' },
  { to: 'practice/floors', title: '평면도 편집', meta: '층을 만들고 벽·복도를 칠하고 방을 놓아요' },
  { to: 'practice/rooms', title: '방 관리', meta: '방 이름, 수용 인원, 장비, 점검 중 표시' },
  { to: 'practice/policy', title: '예약 정책', meta: '운영 시간, 시간 단위, 이용 한도, 예약 오픈' },
]

const ACADEMY_ADMIN_MENU = [
  { to: 'academy/students', title: '원생', meta: '원생 등록, 수강 관리, 만료 임박' },
  { to: 'academy/catalog', title: '과목·상품', meta: '과목과 수업 상품, 가격' },
]
const TEACHING_MENU = [{ to: 'academy/my-students', title: '담당 학생', meta: '맡은 학생과 레슨 기록' }]
const STUDENT_ACADEMY_MENU = [{ to: 'academy/me', title: '내 수강', meta: '수강 정보와 레슨 기록' }]

const MANAGER_MENU = [
  { to: 'members', title: '멤버 관리', meta: '가입 신청 승인, 역할 변경, 비활성화' },
  { to: 'invite', title: '초대 링크', meta: '역할을 정해 한 번 쓰는 링크를 만들어요' },
  { to: 'join-code', title: '가입 코드', meta: '코드를 받은 사람이 신청하면 승인해요' },
]

export function OrganizationHomePage() {
  const { orgId } = useParams()
  const org = useMyOrganization(orgId)

  if (org === undefined) return <main className="page" />
  if (org === null) return <NotMember />

  // 학생은 예약 메뉴, 관리자는 관리 메뉴, 강사는 지도 보기만 (MVP에서 예약은 학생만, Q2)
  // 관리자도 직접 가르칠 수 있어서(1인 학원) 담당 학생 메뉴를 함께 둔다
  const practice = org.modules.includes('PRACTICE_ROOM')
  const academy = org.modules.includes('ACADEMY')
  const menu = isManager(org.role)
    ? [...(academy ? [...ACADEMY_ADMIN_MENU, ...TEACHING_MENU] : []), ...(practice ? PRACTICE_MENU : []), ...MANAGER_MENU]
    : org.role === 'STUDENT'
      ? [...(practice ? BOOKING_MENU : []), ...(academy ? STUDENT_ACADEMY_MENU : [])]
      : [...(academy ? TEACHING_MENU : []), ...(practice ? PRACTICE_MENU.slice(0, 1) : [])]

  return (
    <main className="page">
      <h1>{org.name}</h1>
      <p className="card-meta">내 역할: {ROLE_LABEL[org.role]}</p>
      {menu.length > 0 ? (
        <ul className="list section">
          {menu.map((item) => (
            <li key={item.to}>
              <Link className="card card-link" to={item.to}>
                <p className="card-title">{item.title}</p>
                <p className="card-meta">{item.meta}</p>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="card empty section">이 기관에서 쓸 수 있는 메뉴가 아직 없어요.</p>
      )}
      <p className="helper"><Link to="/">내 기관으로</Link></p>
    </main>
  )
}

export function NotMember() {
  return (
    <main className="page">
      <p className="alert" role="alert">이 기관의 멤버가 아니거나 아직 승인되지 않았어요.</p>
      <p className="helper"><Link to="/">내 기관으로</Link></p>
    </main>
  )
}
