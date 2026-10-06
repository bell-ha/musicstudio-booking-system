import { googleLoginEnabled, startGoogleLogin } from './googleLogin'

/** 보조 버튼. 클라이언트 ID가 설정되지 않았으면 보이지 않는다 */
export function GoogleButton() {
  if (!googleLoginEnabled) return null
  return (
    <button type="button" className="button button-block" onClick={startGoogleLogin}>
      Google로 계속하기
    </button>
  )
}
