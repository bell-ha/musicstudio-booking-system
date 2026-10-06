import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { token } from './api'

/** 로그인하지 않았으면 로그인 화면으로 보낸다. 화면에 들어올 때마다 토큰을 다시 본다 */
export function RequireLogin({ children }: { children: ReactNode }) {
  return token.get() ? children : <Navigate to="/login" replace />
}
