import { splitLinks } from './linkify'

/** 평문 속 http(s) 주소만 링크로 바꾼다. 나머지는 React 텍스트라 이스케이프된다 (HTML·마크다운 없음) */
export function Linkified({ text }: { text: string }) {
  return (
    <>
      {splitLinks(text).map((part, i) =>
        i % 2 === 1
          ? <a key={i} href={part} target="_blank" rel="noopener noreferrer nofollow">{part}</a>
          : part)}
    </>
  )
}
