/** 시작하기 목록에 필요한 개수. 이미 있는 목록 API 응답에서 뽑는다 (켜지 않은 모듈은 undefined) */
export type SetupData = {
  products?: number
  students?: number
  enrolled?: number
  placedRooms?: number
  people: number // 활동 중인 강사·학생
  siteDecorated: boolean // 로고나 소개가 있음
}

export type Step = { key: string; title: string; meta: string; to: string; done: boolean }

/**
 * UC-15 시작하기 목록. 순서는 의존 순서(UC-42: 상품 → 원생 → 수강), 막는 것이 없는 사이트 꾸미기는 맨 끝.
 * 완료는 사람이 체크하지 않고 데이터로 판단한다(체크했는데 상품이 없는 일이 없게).
 */
export function setupSteps(modules: string[], d: SetupData): Step[] {
  const steps: Step[] = []
  if (modules.includes('ACADEMY')) {
    steps.push({ key: 'product', title: '수업 상품 만들기', meta: '과목과 가격을 정해요. 수강을 넣으려면 먼저 있어야 해요', to: 'academy/catalog', done: (d.products ?? 0) > 0 })
    steps.push({
      key: 'enroll', title: '원생 등록하고 수강 넣기',
      meta: modules.includes('BILLING') ? '수강을 넣으면 레슨 일정과 청구서가 이어져요' : '수강을 넣으면 레슨 일정이 이어져요',
      to: (d.students ?? 0) > 0 ? 'academy/students' : 'academy/students/new', done: (d.enrolled ?? 0) > 0,
    })
  }
  if (modules.includes('PRACTICE_ROOM')) {
    steps.push({ key: 'rooms', title: '평면도에 방 놓기', meta: '학생이 지도에서 이 방을 예약해요', to: 'practice/floors', done: (d.placedRooms ?? 0) > 0 })
  }
  steps.push({ key: 'people', title: '강사·학생 들이기', meta: '초대 링크나 가입 코드로 들어와요. 혼자 가르치고 학생 계정을 안 쓰면 숨겨도 돼요', to: 'invite', done: d.people > 0 })
  steps.push({ key: 'site', title: '기관 꾸미기', meta: '로고·소개·기관 색, 로그인 없이 보는 소개 페이지', to: 'site', done: d.siteDecorated })
  return steps
}
