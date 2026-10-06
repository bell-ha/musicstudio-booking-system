import { describe, expect, it } from 'vitest'
import { splitLinks } from './linkify'

const links = (text: string) => splitLinks(text).filter((_, i) => i % 2 === 1)

describe('splitLinks', () => {
  it('주소 뒤에 붙은 한국어 조사는 주소가 아니다', () => {
    expect(splitLinks('카페 https://cafe.naver.com/harmony에서 확인하세요'))
      .toEqual(['카페 ', 'https://cafe.naver.com/harmony', '에서 확인하세요'])
  })

  it('끝 문장부호와 닫는 괄호는 문장으로 돌린다', () => {
    expect(splitLinks('(https://a.com).')).toEqual(['(', 'https://a.com', ').'])
    expect(links('https://a.com/x?y=1, 감사합니다')).toEqual(['https://a.com/x?y=1'])
  })

  it('http(s)가 아닌 스킴은 링크가 아니다', () => {
    expect(links('javascript:alert(1) 그리고 data:text/html,x')).toEqual([])
  })

  it('주소가 여러 개여도 순서대로 나눈다', () => {
    expect(links('앞 http://a.kr 가운데 https://b.kr/c?d=e#f 끝')).toEqual(['http://a.kr', 'https://b.kr/c?d=e#f'])
  })

  it('합치면 원문 그대로다', () => {
    const text = '카페 https://cafe.naver.com/harmony에서 (https://a.com). 끝'
    expect(splitLinks(text).join('')).toBe(text)
  })
})
