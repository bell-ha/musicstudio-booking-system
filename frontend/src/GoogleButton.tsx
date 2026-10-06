import { googleLoginEnabled, startGoogleLogin } from './googleLogin'

/**
 * 구글 로그인 버튼. 로그인·가입 화면 맨 위에 늘 보인다.
 * 클라이언트 ID가 아직 없으면(.env의 GOOGLE_CLIENT_ID) 누를 수 없게 두고 이유를 적는다.
 * 모양은 구글의 로그인 버튼 안내를 따른다(흰 바탕, 공식 G 로고, "Google로 계속하기").
 */
export function GoogleButton({ label = 'Google로 계속하기' }: { label?: string }) {
  return (
    <div className="google-login">
      <button type="button" className="button-google" onClick={() => startGoogleLogin()} disabled={!googleLoginEnabled}>
        <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
          <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
          <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
          <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
          <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
        </svg>
        {label}
      </button>
      {!googleLoginEnabled && <p className="hint">구글 로그인 설정이 아직 안 됐어요. 관리자가 구글 키를 넣으면 켜져요.</p>}
      <div className="divider" role="separator"><span>또는 이메일로</span></div>
    </div>
  )
}
