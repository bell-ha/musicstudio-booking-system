/**
 * 기관 색 계산 (DESIGN 0.2 2절). 서버의 SiteColors와 같은 대비 공식을 쓴다.
 * 원장이 고른 아무 색에서 primary·pressed·tint 세 토큰을 만든다.
 */
export const MIN_CONTRAST = 4.5

type RGB = [number, number, number]

const toRgb = (hex: string): RGB => {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}
const toHex = (rgb: RGB) => '#' + rgb.map((c) => Math.round(c).toString(16).padStart(2, '0')).join('').toUpperCase()
/** t만큼 target 쪽으로 섞는다 (0이면 그대로, 1이면 target) */
const mix = (hex: string, target: RGB, t: number) => toHex(toRgb(hex).map((c, i) => c + (target[i] - c) * t) as RGB)

/** WCAG 2 상대 휘도와 흰 글자 대비 */
export function contrastWithWhite(hex: string): number {
  const [r, g, b] = toRgb(hex).map((v) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 1.05 / (0.2126 * r + 0.7152 * g + 0.0722 * b + 0.05)
}

/** 흰 글자가 안 보이면 검정 쪽으로 조금씩 섞어 4.5:1을 넘기는 가장 밝은 색을 찾는다. 색조는 그대로다 */
export function readable(hex: string): { color: string; adjusted: boolean } {
  if (contrastWithWhite(hex) >= MIN_CONTRAST) return { color: hex.toUpperCase(), adjusted: false }
  for (let t = 0.02; t <= 1; t += 0.02) {
    const darker = mix(hex, [0, 0, 0], t)
    if (contrastWithWhite(darker) >= MIN_CONTRAST) return { color: darker, adjusted: true }
  }
  return { color: '#000000', adjusted: true }
}

/** 예약 상태 색(초록 빈 방, 빨강 오류, 주황 경고)과 색조가 가까운가. 막지는 않고 알려 준다 */
export function nearStatusHue(hex: string): boolean {
  const [r, g, b] = toRgb(hex).map((v) => v / 255)
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  if (max - min < 0.15) return false // 회색 계열은 색조가 없다
  const d = max - min
  const h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4
  const hue = (h * 60 + 360) % 360
  return hue < 50 || hue >= 340 || (hue >= 90 && hue < 175)
}

/** 누른 색은 조금 진하게, 옅은 면은 흰색 쪽으로 */
export function shades(hex: string) {
  return { primary: hex, pressed: mix(hex, [0, 0, 0], 0.15), tint: mix(hex, [255, 255, 255], 0.9) }
}
