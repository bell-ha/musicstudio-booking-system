// 구글 로그인 (ADR 0013). 인가 코드 + state + nonce. 코드 교환과 id_token 검증은 백엔드가 한다.

const CLIENT_ID: string | undefined = import.meta.env.VITE_GOOGLE_CLIENT_ID
const STORAGE_KEY = 'googleLogin'

export const googleLoginEnabled = Boolean(CLIENT_ID)

export const googleRedirectUri = () => `${location.origin}/auth/callback/google`

function randomString() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** mode: login(구글로 로그인) 또는 link(로그인한 계정에 구글 연결). 돌아왔을 때 무엇을 할지 함께 저장한다 */
export function startGoogleLogin(mode: 'login' | 'link' = 'login') {
  const state = randomString()
  const nonce = randomString()
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ state, nonce, mode }))
  const params = new URLSearchParams({
    client_id: CLIENT_ID!,
    redirect_uri: googleRedirectUri(),
    response_type: 'code',
    scope: 'openid email profile',
    state,
    nonce,
  })
  location.assign(`https://accounts.google.com/o/oauth2/v2/auth?${params}`)
}

/** 저장해 둔 state·nonce를 한 번만 꺼낸다. 같은 값으로 다시 시도하지 못하게 바로 지운다 */
export function takeGoogleLoginRequest(): { state: string; nonce: string; mode?: 'login' | 'link' } | null {
  const saved = sessionStorage.getItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
  return saved ? JSON.parse(saved) : null
}
