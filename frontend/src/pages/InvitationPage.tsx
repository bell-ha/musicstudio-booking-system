import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../api'
import { Field } from '../Field'
import { isManager, ROLE_LABEL, type Role } from '../labels'
import { useMyOrganization } from '../useMyOrganization'
import { NotMember } from './OrganizationHomePage'

export function InvitationPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [link, setLink] = useState<{ url: string; expiresAt: string } | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [copied, setCopied] = useState(false)

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  // 관리자 초대는 소유자만 만든다
  const roles: Role[] = me.role === 'OWNER' ? ['STUDENT', 'TEACHER', 'MANAGER'] : ['STUDENT', 'TEACHER']

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setErrors({})
    setMessage('')
    setCopied(false)
    try {
      const created = await api<{ token: string; expiresAt: string }>(`/organizations/${orgId}/invitations`, {
        method: 'POST',
        body: { role: form.get('role'), expiresInDays: Number(form.get('expiresInDays')) },
      })
      // 토큰은 # 뒤에 둔다. 서버 접근 로그와 Referer에 남지 않는다
      setLink({ url: `${location.origin}/invite#${created.token}`, expiresAt: created.expiresAt })
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  async function copy() {
    if (!link) return
    await navigator.clipboard.writeText(link.url)
    setCopied(true)
  }

  return (
    <main className="page">
      <h1>초대 링크</h1>
      <form className="form" onSubmit={submit}>
        <Field id="role" label="역할" error={errors.role}>
          <select id="role" name="role" defaultValue="STUDENT">
            {roles.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </Field>
        <Field id="expiresInDays" label="유효 기간 (일)" error={errors.expiresInDays}>
          <input id="expiresInDays" name="expiresInDays" type="number" min={1} max={30} defaultValue={7} required />
        </Field>
        {message && <p className="alert" role="alert">{message}</p>}
        <button className="button button-primary">초대 링크 만들기</button>
      </form>

      {link && (
        <div className="card section">
          <p className="card-title">한 사람만 쓸 수 있는 링크예요</p>
          <p className="card-meta">
            {new Date(link.expiresAt).toLocaleString('ko-KR')}까지 유효해요. 이 화면을 나가면 다시 볼 수 없어요.
          </p>
          <input className="input-readonly" readOnly value={link.url} aria-label="초대 링크"
            onFocus={(e) => e.currentTarget.select()} />
          <button className="button button-block" onClick={copy}>{copied ? '복사했어요' : '링크 복사'}</button>
        </div>
      )}
    </main>
  )
}
