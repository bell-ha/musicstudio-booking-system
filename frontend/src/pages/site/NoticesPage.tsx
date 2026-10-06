import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ChevronDown, Megaphone, Pin } from 'lucide-react'
import { useLocation, useParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../../api'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { Linkified } from '../../site/Linkified'
import { dateOf, VISIBILITY_LABEL, type Notice, type Visibility } from '../../site/site'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

type Draft = { id?: number; title: string; body: string; visibility: Visibility; pinned: boolean }
const EMPTY: Draft = { title: '', body: '', visibility: 'MEMBERS', pinned: false }

/** UC-11·12. 목록에서 누르면 본문이 펼쳐진다. 관리자는 같은 화면에서 쓰고 고친다 */
export function NoticesPage() {
  const { orgId } = useParams()
  const { hash } = useLocation()
  const org = useMyOrganization(orgId)
  const [notices, setNotices] = useState<Notice[]>()
  const [open, setOpen] = useState<number | null>(() => Number(hash.slice(1)) || null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')

  const load = useCallback(() => {
    api<Notice[]>(`/organizations/${orgId}/notices`).then(setNotices).catch((e) => setMessage(errorMessage(e)))
  }, [orgId])
  useEffect(load, [load])

  if (org === undefined) return <main className="page page-wide" />
  if (org === null) return <NotMember />
  const manager = isManager(org.role)

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!draft) return
    setErrors({})
    setMessage('')
    try {
      const body = { title: draft.title, body: draft.body, visibility: draft.visibility, pinned: draft.pinned }
      const saved = draft.id
        ? await api<Notice>(`/organizations/${orgId}/notices/${draft.id}`, { method: 'PATCH', body })
        : await api<Notice>(`/organizations/${orgId}/notices`, { method: 'POST', body })
      setDraft(null)
      setOpen(saved.id)
      load()
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  async function remove(notice: Notice) {
    if (!window.confirm(`"${notice.title}" 공지를 지울까요? 되돌릴 수 없어요.`)) return
    try {
      await api(`/organizations/${orgId}/notices/${notice.id}`, { method: 'DELETE' })
      load()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  return (
    <main className="page page-wide">
      <div className="app-bar">
        <h1>공지</h1>
        {manager && !draft && <button className="button button-primary" onClick={() => setDraft(EMPTY)}>새 공지</button>}
      </div>

      {draft && (
        <form className="card form section" onSubmit={save} noValidate>
          <p className="card-title">{draft.id ? '공지 고치기' : '새 공지'}</p>
          <Field id="title" label="제목" error={errors.title}>
            <input id="title" maxLength={100} value={draft.title} required
              onChange={(e) => setDraft({ ...draft, title: e.target.value })} />
          </Field>
          <Field id="body" label="내용" error={errors.body}>
            <textarea id="body" rows={6} maxLength={5000} value={draft.body}
              onChange={(e) => setDraft({ ...draft, body: e.target.value })} />
          </Field>
          <fieldset className="segmented" aria-label="공개 범위">
            {(['STAFF', 'MEMBERS', 'PUBLIC'] as const).map((v) => (
              <label key={v} className={draft.visibility === v ? 'segment segment-on' : 'segment'}>
                <input type="radio" name="visibility" checked={draft.visibility === v}
                  onChange={() => setDraft({ ...draft, visibility: v })} />
                {VISIBILITY_LABEL[v]}
              </label>
            ))}
          </fieldset>
          {draft.visibility === 'PUBLIC' && (
            <p className="hint">로그인하지 않은 사람도 공개 소개 페이지에서 볼 수 있어요.</p>
          )}
          <label className="check">
            <input type="checkbox" checked={draft.pinned} onChange={(e) => setDraft({ ...draft, pinned: e.target.checked })} />
            맨 위에 고정
          </label>
          {message && <p className="alert" role="alert">{message}</p>}
          <div className="actions">
            <button className="button button-primary">{draft.id ? '저장' : '올리기'}</button>
            <button type="button" className="button" onClick={() => { setDraft(null); setMessage('') }}>취소</button>
          </div>
        </form>
      )}

      {!draft && message && <p className="alert" role="alert">{message}</p>}

      {notices && notices.length === 0 && (
        <div className="card empty section">
          <p>아직 공지가 없어요.</p>
          {manager && <button className="button" onClick={() => setDraft(EMPTY)}>첫 공지 쓰기</button>}
        </div>
      )}

      {notices && notices.length > 0 && (
        <ul className="group section">
          {notices.map((n) => (
            <li key={n.id} id={String(n.id)}>
              <button type="button" className="row row-button" aria-expanded={open === n.id}
                onClick={() => setOpen(open === n.id ? null : n.id)}>
                <span className="row-icon">{n.pinned ? <Pin size={18} /> : <Megaphone size={18} />}</span>
                <span className="row-text">
                  <span className="row-title">{n.title}</span>
                  <span className="row-meta">
                    {dateOf(n.createdAt)}
                    {manager && n.visibility && <span className="badge">{VISIBILITY_LABEL[n.visibility]}</span>}
                  </span>
                </span>
                <ChevronDown className={open === n.id ? 'row-chevron row-chevron-open' : 'row-chevron'} size={20} />
              </button>
              {open === n.id && (
                <div className="notice-body">
                  {n.body ? <p><Linkified text={n.body} /></p> : <p className="card-meta">내용 없음</p>}
                  {manager && (
                    <div className="actions">
                      <button className="button" onClick={() => setDraft({ id: n.id, title: n.title, body: n.body,
                        visibility: n.visibility ?? 'MEMBERS', pinned: n.pinned })}>고치기</button>
                      <button className="button button-danger" onClick={() => remove(n)}>지우기</button>
                    </div>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
