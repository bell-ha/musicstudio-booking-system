// 로그인이 필요해서 로그인 화면으로 보낼 때, 로그인한 뒤 돌아올 화면을 기억한다 (예: 초대 수락)
const KEY = 'returnTo'

export function rememberReturnTo(path: string) {
  sessionStorage.setItem(KEY, path)
}

/** 기억한 화면을 한 번만 꺼낸다. 이 사이트 안의 경로만 받는다 */
export function takeReturnTo(): string {
  const path = sessionStorage.getItem(KEY)
  sessionStorage.removeItem(KEY)
  return path && path.startsWith('/') && !path.startsWith('//') ? path : '/'
}
