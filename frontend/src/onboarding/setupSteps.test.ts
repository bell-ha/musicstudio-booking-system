import { describe, expect, it } from 'vitest'
import { setupSteps, type SetupData } from './setupSteps'

const empty: SetupData = { products: 0, students: 0, enrolled: 0, placedRooms: 0, people: 0, siteDecorated: false }
const keys = (modules: string[]) => setupSteps(modules, empty).map((s) => s.key)

describe('setupSteps', () => {
  it('켠 모듈에 맞는 단계만 의존 순서로', () => {
    expect(keys(['PRACTICE_ROOM', 'ACADEMY'])).toEqual(['product', 'enroll', 'rooms', 'people', 'site'])
    expect(keys(['ACADEMY', 'BILLING'])).toEqual(['product', 'enroll', 'people', 'site'])
    expect(keys(['PRACTICE_ROOM'])).toEqual(['rooms', 'people', 'site'])
    expect(keys([])).toEqual(['people', 'site'])
  })

  it('완료는 데이터로 판단한다', () => {
    const steps = setupSteps(['PRACTICE_ROOM', 'ACADEMY'], { ...empty, products: 2, placedRooms: 1, siteDecorated: true })
    expect(steps.filter((s) => s.done).map((s) => s.key)).toEqual(['product', 'rooms', 'site'])
  })

  it('원생이 없으면 등록 화면으로, 있으면 원생 목록으로', () => {
    expect(setupSteps(['ACADEMY'], empty)[1].to).toBe('academy/students/new')
    expect(setupSteps(['ACADEMY'], { ...empty, students: 1 })[1].to).toBe('academy/students')
  })

  it('청구·결제가 켜져 있을 때만 청구서를 말한다', () => {
    expect(setupSteps(['ACADEMY', 'BILLING'], empty)[1].meta).toContain('청구서')
    expect(setupSteps(['ACADEMY'], empty)[1].meta).not.toContain('청구서')
  })
})
