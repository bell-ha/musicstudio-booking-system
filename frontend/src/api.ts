// fetch를 감싼 함수 하나. 토큰은 localStorage, 오류는 Problem Details 그대로 던진다.
import { rememberReturnTo } from './returnTo'

const TOKEN_KEY = 'accessToken'

export type Problem = {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.title ?? `HTTP ${problem.status}`)
    this.problem = problem
  }
}

export const token = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (value: string) => localStorage.setItem(TOKEN_KEY, value),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export async function api<T>(
  path: string,
  init: { method?: string; body?: unknown; headers?: Record<string, string> } = {},
): Promise<T> {
  // 파일(Blob)은 바이트 그대로 보낸다 (로고 업로드). 형식은 파일의 것을 쓰고, 그 밖의 본문은 JSON
  const raw = init.body instanceof Blob
  const headers: Record<string, string> = { ...init.headers }
  if (init.body !== undefined && !raw) headers['Content-Type'] = 'application/json'
  if (raw && !headers['Content-Type']) headers['Content-Type'] = (init.body as Blob).type || 'application/octet-stream'
  // 가입·로그인에는 토큰을 보내지 않는다. 만료된 토큰이 붙으면 서버가 permitAll 경로에서도 401을 준다
  const accessToken = token.get()
  if (accessToken && !path.startsWith('/auth/')) headers.Authorization = `Bearer ${accessToken}`

  const response = await fetch(`/api/v1${path}`, {
    method: init.method ?? 'GET',
    headers,
    body: init.body === undefined ? undefined : raw ? (init.body as Blob) : JSON.stringify(init.body),
  })

  if (!response.ok) {
    const problem: Problem = response.headers.get('Content-Type')?.includes('json')
      ? await response.json()
      : { status: response.status }
    if (response.status === 401 && !path.startsWith('/auth/')) {
      // 토큰이 만료됐다. 로그인한 뒤 지금 화면으로 돌아온다
      token.clear()
      rememberReturnTo(location.pathname + location.search)
      location.replace('/login')
    }
    throw new ApiError(problem)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

/** 입력칸 아래에 보여 줄 필드별 오류 (400 validation-failed) */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries((error.problem.errors ?? []).map((e) => [e.field, e.message]))
}

// 서버 문구보다 화면에 맞는 문구가 필요한 code
const CODE_MESSAGES: Record<string, string> = {
  INVITATION_INVALID: '초대 링크가 만료됐거나 이미 사용됐어요. 관리자에게 새 링크를 받아 주세요.',
  JOIN_CODE_INVALID: '가입 코드를 다시 확인해 주세요.',
  ALREADY_MEMBER: '이미 이 기관의 멤버예요.',
  ALREADY_PENDING: '이미 신청했어요. 관리자의 승인을 기다려 주세요.',
  MEMBER_INACTIVE: '이 기관에서 비활성화된 계정이에요. 관리자에게 문의해 주세요.',
  LAST_OWNER: '기관에는 소유자가 한 명 이상 있어야 해요.',
  NOT_A_MEMBER: '이 기관의 멤버가 아니에요.',
  FORBIDDEN: '권한이 없어요.',
  CONFLICTING_UPDATE: '다른 관리자가 먼저 바꿨어요. 최신 내용으로 다시 불러왔어요. 확인한 뒤 다시 해 주세요.',
}

/** 폼 위에 보여 줄 한 줄 오류 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const { code, detail, title } = error.problem
    return (code && CODE_MESSAGES[code]) ?? detail ?? title ?? '요청을 처리하지 못했어요.'
  }
  return '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.'
}
