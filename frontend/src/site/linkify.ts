/**
 * 평문을 [글, 주소, 글, 주소, …]로 나눈다 (홀수 번째가 주소).
 * - http(s)만 잡는다. javascript: 같은 스킴은 링크가 되지 않는다.
 * - 주소에 쓸 수 있는 ASCII 문자만 잡는다. 한국어 글은 주소 바로 뒤에 조사가 붙는다("…/harmony에서").
 * - 끝 문장부호(. , ! ? : ; ' " ) ])는 문장의 것으로 보고 뒤 글로 돌린다. 괄호 쌍이 든 주소(위키)는 MVP에서 포기한다.
 */
const URL_PATTERN = /(https?:\/\/[A-Za-z0-9\-._~:/?#[\]@!$&'*+,;=%]+)/g
const TRAILING = /[.,!?:;'")\]]+$/

export function splitLinks(text: string): string[] {
  const parts = text.split(URL_PATTERN)
  for (let i = 1; i < parts.length; i += 2) {
    const tail = parts[i].match(TRAILING)?.[0]
    if (tail) {
      parts[i] = parts[i].slice(0, -tail.length)
      parts[i + 1] = tail + parts[i + 1]
    }
  }
  return parts
}
