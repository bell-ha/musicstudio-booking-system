import type { CSSProperties } from 'react'
import { shades } from './color'
import type { OrgType } from '../labels'

export type PresetColor = 'INDIGO' | 'BLUE' | 'SKY' | 'VIOLET' | 'PLUM' | 'NAVY'
/** 프리셋 키 또는 원장이 고른 #RRGGBB */
export type SiteColor = PresetColor | string

/** API 37. published·slug는 관리자 이상, acceptJoin은 소유자에게만 온다 */
export type Site = {
  logoUrl: string | null
  intro: string
  address: string
  phone: string
  hoursText: string
  color: SiteColor
  published: boolean | null
  slug: string | null
  acceptJoin: boolean | null
}

export type Visibility = 'STAFF' | 'MEMBERS' | 'PUBLIC'

export type Notice = {
  id: number
  title: string
  body: string
  visibility?: Visibility
  pinned: boolean
  createdAt: string
}

/** API 46 */
export type PublicSite = {
  name: string
  type: OrgType
  logoUrl: string | null
  intro: string
  address: string
  phone: string
  hoursText: string
  color: SiteColor
  notices: Notice[]
  join: { open: boolean; form: { key: string; label: string; required: boolean }[] }
}

/**
 * 기관 색 빠른 선택 (DESIGN 0.2 2절). 흰 글자 대비가 모두 WCAG AA 이상이고, 상태 색(초록·빨강·주황)과 겹치지 않는 6색.
 * 원장은 이 밖의 색도 고를 수 있다(color.ts가 대비를 맞춘다). 바뀌는 것은 primary 세 토큰뿐이고 예약 상태 색은 그대로다.
 */
export const COLORS: Record<PresetColor, { name: string; primary: string; pressed: string; tint: string }> = {
  INDIGO: { name: '인디고', primary: '#4F46E5', pressed: '#4338CA', tint: '#EEF2FF' },
  BLUE: { name: '파랑', primary: '#1D4ED8', pressed: '#1E40AF', tint: '#EBEFFB' },
  SKY: { name: '하늘', primary: '#0369A1', pressed: '#075985', tint: '#E8F2F7' },
  VIOLET: { name: '보라', primary: '#6D28D9', pressed: '#5B21B6', tint: '#F2ECFC' },
  PLUM: { name: '자두', primary: '#86198F', pressed: '#701A75', tint: '#F4EAF5' },
  NAVY: { name: '남색', primary: '#1E3A8A', pressed: '#172554', tint: '#EBEDF4' },
}

/** 기관 화면·공개 페이지 루트에 거는 CSS 변수 */
export function colorStyle(color: SiteColor | undefined): CSSProperties {
  const preset = COLORS[(color ?? 'INDIGO') as PresetColor]
  const c = preset ?? shades(color!)
  return { '--primary': c.primary, '--primary-pressed': c.pressed, '--primary-tint': c.tint } as CSSProperties
}

export const VISIBILITY_LABEL: Record<Visibility, string> = { STAFF: '강사 이상', MEMBERS: '멤버', PUBLIC: '외부 공개' }

export const dateOf = (iso: string) =>
  new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
