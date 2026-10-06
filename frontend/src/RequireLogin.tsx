import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { token } from './api'
import { rememberReturnTo } from './returnTo'

/** 로그인하지 않았으면 로그인 화면으로 보낸다. 화면에 들어올 때마다 토큰을 다시 본다 */
export function RequireLogin({ children }: { children: ReactNode }) {
  const location = useLocation()
  if (token.get()) return children
  rememberReturnTo(location.pathname + location.search)
  return <Navigate to="/login" replace />
}
