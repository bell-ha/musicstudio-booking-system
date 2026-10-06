// fetch를 감싼 함수 하나. 토큰은 localStorage, 오류는 Problem Details 그대로 던진다.

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

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (init.body !== undefined) headers['Content-Type'] = 'application/json'
  // 가입·로그인에는 토큰을 보내지 않는다. 만료된 토큰이 붙으면 서버가 permitAll 경로에서도 401을 준다
  const accessToken = token.get()
  if (accessToken && !path.startsWith('/auth/')) headers.Authorization = `Bearer ${accessToken}`

  const response = await fetch(`/api/v1${path}`, {
    method: init.method ?? 'GET',
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })

  if (!response.ok) {
    const problem: Problem = response.headers.get('Content-Type')?.includes('json')
      ? await response.json()
      : { status: response.status }
    if (response.status === 401) token.clear()
    throw new ApiError(problem)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

/** 입력칸 아래에 보여 줄 필드별 오류 (400 validation-failed) */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries((error.problem.errors ?? []).map((e) => [e.field, e.message]))
}

/** 폼 위에 보여 줄 한 줄 오류 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.problem.detail ?? error.problem.title ?? '요청을 처리하지 못했어요.'
  return '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.'
}
