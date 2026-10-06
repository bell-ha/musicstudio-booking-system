import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../api'
import { Field } from '../Field'
import type { PublicSite } from '../site/site'

type Lookup = { organizationName: string; joinForm: { key: string; label: string; required: boolean }[] }

/**
 * UC-06. 코드 확인 → 기관이 정한 정보 입력 → 신청.
 * UC-14. 공개 소개 페이지에서 오면(?slug=) 코드 단계를 건너뛰고 주소로 신청한다. 가입 코드는 보지 않는다.
 */
export function JoinRequestPage() {
  const [code, setCode] = useState('')
  const [lookup, setLookup] = useState<Lookup | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const slug = useSearchParams()[0].get('slug')

  useEffect(() => {
    if (!slug) return
    api<PublicSite>(`/public/sites/${encodeURIComponent(slug)}`)
      .then((site) => site.join.open
        ? setLookup({ organizationName: site.name, joinForm: site.join.form })
        : setMessage('지금은 이 페이지에서 가입 신청을 받지 않아요.'))
      .catch(() => setMessage('찾을 수 없는 페이지예요.'))
  }, [slug])

  async function send(request: () => Promise<void>) {
    setSubmitting(true)
    setErrors({})
    setMessage('')
    try {
      await request()
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  function checkCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    send(async () => {
      setLookup(await api<Lookup>('/join-requests/lookup', { method: 'POST', body: { code } }))
    })
  }

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const answers = Object.fromEntries(lookup!.joinForm.map((item) => [item.key, String(form.get(item.key) ?? '').trim()]))
    send(async () => {
      const created = slug
        ? await api<{ organizationName: string }>(`/sites/${encodeURIComponent(slug)}/join-requests`, { method: 'POST', body: { answers } })
        : await api<{ organizationName: string }>('/join-requests', { method: 'POST', body: { code, answers } })
      setDone(created.organizationName)
    })
  }

  if (done) {
    return (
      <main className="page">
        <h1>신청했어요</h1>
        <p>{done}의 관리자가 승인하면 들어갈 수 있어요.</p>
        <p className="helper"><Link to="/">내 기관으로</Link></p>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>{slug ? '가입 신청하기' : '가입 코드로 신청하기'}</h1>
      {slug && !lookup ? (
        message && <p className="alert" role="alert">{message}</p>
      ) : !lookup ? (
        <form className="form" onSubmit={checkCode}>
          <Field id="code" label="가입 코드" error={errors.code}>
            <input id="code" value={code} onChange={(e) => setCode(e.target.value)}
              autoCapitalize="characters" autoComplete="off" required />
          </Field>
          {message && <p className="alert" role="alert">{message}</p>}
          <button className="button button-primary" disabled={submitting}>다음</button>
        </form>
      ) : (
        <form className="form" onSubmit={apply} noValidate>
          <p className="card-title">{lookup.organizationName}</p>
          {lookup.joinForm.map((item) => (
            <Field key={item.key} id={item.key} label={item.required ? item.label : `${item.label} (선택)`}
              error={errors[`answers.${item.key}`]}>
              <input id={item.key} name={item.key} maxLength={100} required={item.required} />
            </Field>
          ))}
          {message && <p className="alert" role="alert">{message}</p>}
          <button className="button button-primary" disabled={submitting}>신청하기</button>
        </form>
      )}
      <p className="helper"><Link to="/">내 기관으로</Link></p>
    </main>
  )
}
