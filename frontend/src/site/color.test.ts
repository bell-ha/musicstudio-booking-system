import { describe, expect, it } from 'vitest'
import { contrastWithWhite, nearStatusHue, readable, shades } from './color'

describe('기관 색', () => {
  it('WCAG 대비를 서버와 같은 값으로 계산한다', () => {
    expect(contrastWithWhite('#767676')).toBeGreaterThanOrEqual(4.5)
    expect(contrastWithWhite('#777777')).toBeLessThan(4.5)
    expect(contrastWithWhite('#1D4ED8')).toBeCloseTo(6.7, 1)
  })

  it('밝은 색은 흰 글자가 보일 때까지 진하게 맞춘다', () => {
    const { color, adjusted } = readable('#FFD400')
    expect(adjusted).toBe(true)
    expect(contrastWithWhite(color)).toBeGreaterThanOrEqual(4.5)
    expect(readable('#1d4ed8')).toEqual({ color: '#1D4ED8', adjusted: false })
  })

  it('상태 색(초록·빨강·주황)과 가까운 색조를 알아챈다', () => {
    expect(nearStatusHue('#047857')).toBe(true) // 초록
    expect(nearStatusHue('#B91C1C')).toBe(true) // 빨강
    expect(nearStatusHue('#1D4ED8')).toBe(false) // 파랑
    expect(nearStatusHue('#555555')).toBe(false) // 회색
  })

  it('누른 색은 더 진하고 옅은 면은 거의 흰색이다', () => {
    const s = shades('#1D4ED8')
    expect(contrastWithWhite(s.pressed)).toBeGreaterThan(contrastWithWhite(s.primary))
    expect(contrastWithWhite(s.tint)).toBeLessThan(1.3)
  })
})
