/**
 * 평문 속 http(s) 주소만 링크로 바꾼다. 나머지는 React 텍스트라 이스케이프된다 (HTML·마크다운 없음).
 * javascript: 같은 스킴은 http(s)로 시작하지 않으므로 링크가 되지 않는다.
 */
const URL_PATTERN = /(https?:\/\/[^\s<>"']+)/g

export function Linkified({ text }: { text: string }) {
  return (
    <>
      {text.split(URL_PATTERN).map((part, i) =>
        i % 2 === 1
          ? <a key={i} href={part} target="_blank" rel="noopener noreferrer nofollow">{part}</a>
          : part)}
    </>
  )
}
