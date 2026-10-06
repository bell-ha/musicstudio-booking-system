import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, errorMessage, fieldErrors } from '../api'
import { Field } from '../Field'
import { TYPE_LABEL, type OrgType } from '../labels'

const TIMEZONES = Intl.supportedValuesOf('timeZone')

export function NewOrganizationPage() {
  const navigate = useNavigate()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSubmitting(true)
    setErrors({})
    setMessage('')
    try {
      // modules는 보내지 않는다. 서버가 유형별 기본값을 쓴다
      await api('/organizations', {
        method: 'POST',
        body: { name: form.get('name'), type: form.get('type'), timezone: form.get('timezone') },
      })
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.problem.status === 401) {
        navigate('/login', { replace: true })
        return
      }
      setErrors(fieldErrors(error))
      if (!(error instanceof ApiError && error.problem.errors)) setMessage(errorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="page">
      <h1>기관 만들기</h1>
      <form className="form" onSubmit={submit} noValidate>
        <Field id="name" label="기관 이름" error={errors.name}>
          <input id="name" name="name" required />
        </Field>
        <Field id="type" label="유형" error={errors.type}>
          <select id="type" name="type" defaultValue="ACADEMY">
            {(Object.keys(TYPE_LABEL) as OrgType[]).map((type) => (
              <option key={type} value={type}>{TYPE_LABEL[type]}</option>
            ))}
          </select>
        </Field>
        <Field id="timezone" label="시간대" error={errors.timezone}>
          <select id="timezone" name="timezone" defaultValue="Asia/Seoul">
            {TIMEZONES.map((zone) => <option key={zone} value={zone}>{zone}</option>)}
          </select>
        </Field>
        {message && <p className="alert" role="alert">{message}</p>}
        <button className="button button-primary" disabled={submitting}>만들기</button>
      </form>
      <p className="helper"><Link to="/">내 기관으로 돌아가기</Link></p>
    </main>
  )
}
