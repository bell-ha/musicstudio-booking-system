import { BrandMark } from '../BrandMark'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, errorMessage, fieldErrors, token } from '../api'
import { takeReturnTo } from '../returnTo'
import { Field } from '../Field'
import { GoogleButton } from '../GoogleButton'
import { PasswordInput } from '../PasswordInput'

export function SignupPage() {
  const navigate = useNavigate()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const email = String(form.get('email'))
    const password = String(form.get('password'))
    setMessage('')
    // 비밀번호는 가려져 있어 잘못 친 줄 모르고 가입하기 쉽다. 서버에 보내기 전에 두 번 친 것이 같은지 본다
    if (password !== form.get('passwordConfirm')) {
      setErrors({ passwordConfirm: '위에 친 비밀번호와 달라요.' })
      ;(event.currentTarget.elements.namedItem('passwordConfirm') as HTMLInputElement).focus()
      return
    }
    setSubmitting(true)
    setErrors({})
    try {
      await api('/auth/signup', { method: 'POST', body: { email, password, name: form.get('name') } })
    } catch (error) {
      if (error instanceof ApiError && error.problem.code === 'EMAIL_TAKEN') {
        setErrors({ email: '이미 가입한 이메일이에요.' })
      } else {
        setErrors(fieldErrors(error))
        if (!(error instanceof ApiError && error.problem.errors)) setMessage(errorMessage(error))
      }
      setSubmitting(false)
      return
    }
    // 가입하면 바로 로그인한다. 로그인만 실패하면 다시 가입하지 않도록 로그인 화면으로 보낸다
    try {
      const login = await api<{ accessToken: string }>('/auth/login', { method: 'POST', body: { email, password } })
      token.set(login.accessToken)
      navigate(takeReturnTo(), { replace: true })
    } catch {
      navigate('/login', { replace: true, state: { notice: '가입은 됐어요. 로그인해 주세요.' } })
    }
  }

  return (
    <main className="page page-auth">
      <div className="brand"><BrandMark /></div>
      <h1>가입하기</h1>
      <p className="page-sub">마디 — 연습실 예약부터 레슨·수납까지 한곳에서</p>
      <GoogleButton label="Google로 가입하기" />
      <form className="form" onSubmit={submit} noValidate>
        <Field id="name" label="이름" error={errors.name}>
          <input id="name" name="name" autoComplete="name" required />
        </Field>
        <Field id="email" label="이메일" error={errors.email}>
          <input id="email" name="email" type="email" autoComplete="email" required />
        </Field>
        <Field id="password" label="비밀번호 (8자 이상)" error={errors.password}>
          <PasswordInput id="password" name="password" autoComplete="new-password" minLength={8} required />
        </Field>
        <Field id="passwordConfirm" label="비밀번호 확인" error={errors.passwordConfirm}>
          <PasswordInput id="passwordConfirm" name="passwordConfirm" autoComplete="new-password" required
            aria-invalid={!!errors.passwordConfirm} aria-describedby={errors.passwordConfirm ? 'passwordConfirm-error' : undefined} />
        </Field>
        {message && <p className="alert" role="alert">{message}</p>}
        <button className="button button-primary" disabled={submitting}>가입하기</button>
      </form>
      <p className="helper">이미 계정이 있나요? <Link to="/login">로그인</Link></p>
    </main>
  )
}
