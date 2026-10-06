import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { api, ApiError, errorMessage, token } from '../api'
import { rememberReturnTo } from '../returnTo'

const KEY = 'inviteToken'

/**
 * /invite#{token}. 토큰을 주소창에서 sessionStorage로 옮긴 뒤 수락한다.
 * 로그인하지 않았으면 로그인(또는 가입)한 뒤 이 화면으로 돌아온다.
 */
export function InvitePage() {
  const navigate = useNavigate()
  const [message, setMessage] = useState('')
  const started = useRef(false)

  // 렌더링 전에 # 뒤 토큰을 옮겨 둔다. 로그인 화면을 거쳐 돌아오면 주소에는 토큰이 없다
  const fromHash = location.hash.slice(1)
  if (fromHash) {
    sessionStorage.setItem(KEY, fromHash)
    history.replaceState(null, '', location.pathname)
  }
  const loggedIn = Boolean(token.get())

  useEffect(() => {
    if (!loggedIn || started.current) return
    started.current = true
    const invite = sessionStorage.getItem(KEY)
    Promise.resolve()
      .then(() => {
        if (!invite) throw new Error('no token')
        return api<{ organizationId: number }>('/invitations/accept', { method: 'POST', body: { token: invite } })
      })
      .then((accepted) => {
        sessionStorage.removeItem(KEY)
        navigate(`/orgs/${accepted.organizationId}`, { replace: true })
      })
      .catch((error) => {
        // 토큰이 만료된 401이면 api가 로그인 화면으로 보낸다. 다시 돌아와 수락하도록 초대는 남겨 둔다
        if (error instanceof ApiError && error.problem.status === 401) return
        sessionStorage.removeItem(KEY)
        setMessage(invite ? errorMessage(error) : '초대 링크를 다시 열어 주세요.')
      })
  }, [loggedIn, navigate])

  if (!loggedIn) {
    rememberReturnTo('/invite')
    return <Navigate to="/login" replace state={{ notice: '로그인하거나 가입하면 초대를 받을 수 있어요.' }} />
  }

  return (
    <main className="page">
      <h1>초대 받기</h1>
      {message ? (
        <>
          <p className="alert" role="alert">{message}</p>
          <p className="helper"><Link to="/">내 기관으로</Link></p>
        </>
      ) : (
        <p>초대를 확인하는 중이에요…</p>
      )}
    </main>
  )
}
