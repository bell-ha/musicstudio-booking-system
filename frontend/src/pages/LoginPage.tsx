import { BrandMark } from '../BrandMark'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { api, ApiError, errorMessage, token } from '../api'
import { takeReturnTo } from '../returnTo'
import { Field } from '../Field'
import { GoogleButton } from '../GoogleButton'

export function LoginPage() {
  const navigate = useNavigate()
  const notice: string | undefined = useLocation().state?.notice
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSubmitting(true)
    setMessage('')
    try {
      const login = await api<{ accessToken: string }>('/auth/login', {
        method: 'POST',
        body: { email: form.get('email'), password: form.get('password') },
      })
      token.set(login.accessToken)
      navigate(takeReturnTo(), { replace: true })
    } catch (error) {
      setMessage(error instanceof ApiError && error.problem.status === 401
        ? '이메일이나 비밀번호가 맞지 않아요.'
        : errorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="page page-auth">
      <div className="brand"><BrandMark /></div>
      <h1>로그인</h1>
      <p className="page-sub">마디에 다시 오신 걸 환영해요</p>
      {notice && <p className="notice">{notice}</p>}
      <form className="form" onSubmit={submit}>
        <Field id="email" label="이메일">
          <input id="email" name="email" type="email" autoComplete="email" required />
        </Field>
        <Field id="password" label="비밀번호">
          <input id="password" name="password" type="password" autoComplete="current-password" required />
        </Field>
        {message && <p className="alert" role="alert">{message}</p>}
        <button className="button button-primary" disabled={submitting}>로그인</button>
      </form>
      <GoogleButton />
      <p className="helper">처음인가요? <Link to="/signup">가입하기</Link></p>
    </main>
  )
}
