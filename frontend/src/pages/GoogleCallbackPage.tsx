import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, ApiError, token } from '../api'
import { takeReturnTo } from '../returnTo'
import { googleRedirectUri, takeGoogleLoginRequest } from '../googleLogin'

const FAILED = '구글 로그인에 실패했어요. 다시 시도해 주세요.'

function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) return FAILED
  switch (error.problem.code) {
    case 'SOCIAL_EMAIL_IN_USE': return '이미 이메일로 가입한 계정이 있어요. 이메일로 로그인해 주세요.'
    case 'SOCIAL_LOGIN_DISABLED': return '지금은 구글 로그인을 쓸 수 없어요. 이메일로 로그인해 주세요.'
    case 'GOOGLE_ALREADY_LINKED': return '이 구글 계정은 이미 다른 계정에 연결돼 있어요.'
    case 'GOOGLE_ALREADY_SET': return '이 계정에는 이미 다른 구글 계정이 연결돼 있어요.'
    default: return FAILED
  }
}

export function GoogleCallbackPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [message, setMessage] = useState('')
  const started = useRef(false) // 개발 모드(StrictMode)에서 effect가 두 번 돌아도 코드는 한 번만 보낸다

  useEffect(() => {
    if (started.current) return
    started.current = true

    const saved = takeGoogleLoginRequest()
    const code = params.get('code')
    Promise.resolve()
      .then(() => {
        // 사용자가 동의 화면에서 취소하면 code 없이 error=access_denied로 돌아온다
        if (!saved || !code || params.get('state') !== saved.state) throw new Error('state mismatch')
        const body = { code, redirectUri: googleRedirectUri(), nonce: saved.nonce }
        // 연결: 지금 로그인한 계정에 이 구글을 붙인다. 토큰은 그대로
        if (saved.mode === 'link') {
          return api('/me/social/google', { method: 'POST', body }).then(() => navigate('/?linked=google', { replace: true }))
        }
        return api<{ accessToken: string }>('/auth/social/google', { method: 'POST', body })
          .then((login) => {
            token.set(login.accessToken)
            navigate(takeReturnTo(), { replace: true })
          })
      })
      .catch((error) => setMessage(messageFor(error)))
  }, [params, navigate])

  return (
    <main className="page">
      <h1>구글 로그인</h1>
      {message ? (
        <>
          <p className="alert" role="alert">{message}</p>
          <p className="helper"><Link to="/login">로그인 화면으로</Link></p>
        </>
      ) : (
        <p>로그인하는 중이에요…</p>
      )}
    </main>
  )
}
