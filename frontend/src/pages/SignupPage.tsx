import { Music } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, errorMessage, fieldErrors, token } from '../api'
import { takeReturnTo } from '../returnTo'
import { Field } from '../Field'
import { GoogleButton } from '../GoogleButton'

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
    setSubmitting(true)
    setErrors({})
    setMessage('')
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
      <div className="brand" aria-hidden="true"><Music size={28} /></div>
      <h1>가입하기</h1>
      <p className="page-sub">학원·연습실을 한곳에서</p>
      <form className="form" onSubmit={submit} noValidate>
        <Field id="name" label="이름" error={errors.name}>
          <input id="name" name="name" autoComplete="name" required />
        </Field>
        <Field id="email" label="이메일" error={errors.email}>
          <input id="email" name="email" type="email" autoComplete="email" required />
        </Field>
        <Field id="password" label="비밀번호 (8자 이상)" error={errors.password}>
          <input id="password" name="password" type="password" autoComplete="new-password" minLength={8} required />
        </Field>
        {message && <p className="alert" role="alert">{message}</p>}
        <button className="button button-primary" disabled={submitting}>가입하기</button>
      </form>
      <GoogleButton />
      <p className="helper">이미 계정이 있나요? <Link to="/login">로그인</Link></p>
    </main>
  )
}
