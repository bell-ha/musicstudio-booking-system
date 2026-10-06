import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, errorMessage } from '../api'
import { isManager } from '../labels'
import { useMyOrganization } from '../useMyOrganization'
import { NotMember } from './OrganizationHomePage'

type FormItem = { key: string; label: string; required: boolean }
type JoinCode = { joinCode: string; enabled: boolean; joinForm: FormItem[] }

export function JoinCodePage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [saved, setSaved] = useState<JoinCode | null>(null)
  const [enabled, setEnabled] = useState(true)
  const [items, setItems] = useState<FormItem[]>([])
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')

  function load(code: JoinCode) {
    setSaved(code)
    setEnabled(code.enabled)
    setItems(code.joinForm)
  }

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    api<JoinCode>(`/organizations/${orgId}/join-code`)
      .then(load)
      .catch((error) => setMessage(errorMessage(error)))
  }, [me, orgId])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  async function run(request: Promise<JoinCode>, done: string) {
    setMessage('')
    setNotice('')
    try {
      load(await request)
      setNotice(done)
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  function save() {
    // 항목 이름을 key로 쓴다. 멤버 목록에서 신청 답변을 그대로 "학번: 1234"처럼 보여 줄 수 있다
    const joinForm = items
      .map((item) => ({ ...item, label: item.label.trim(), key: item.label.trim() }))
      .filter((item) => item.label)
    run(api(`/organizations/${orgId}/join-code`, { method: 'PATCH', body: { enabled, joinForm } }), '저장했어요.')
  }

  function regenerate() {
    if (!window.confirm('새 코드를 만들면 지금 코드로는 더 이상 신청할 수 없어요. 새로 만들까요?')) return
    run(api(`/organizations/${orgId}/join-code/regenerate`, { method: 'POST' }), '새 코드를 만들었어요.')
  }

  const update = (index: number, patch: Partial<FormItem>) =>
    setItems((list) => list.map((item, i) => (i === index ? { ...item, ...patch } : item)))

  return (
    <main className="page">
      <h1>가입 코드</h1>
      {saved && (
        <div className="form">
          <div className="card">
            <p className="join-code">{saved.joinCode}</p>
            <p className="card-meta">이 코드를 받은 사람이 신청하면 멤버 관리의 승인 대기에 보여요. 승인하면 학생이 돼요.</p>
            <button className="button button-block" onClick={regenerate}>코드 새로 만들기</button>
          </div>

          <label className="check">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            가입 코드로 신청 받기
          </label>

          <fieldset className="card fieldset">
            <legend className="card-title">신청할 때 받을 정보</legend>
            {items.length === 0 && <p className="card-meta">받을 정보가 없으면 이름과 이메일만 보여요.</p>}
            {items.map((item, index) => (
              <div className="form-row" key={index}>
                <input aria-label="항목 이름" placeholder="예: 학번" value={item.label}
                  onChange={(e) => update(index, { label: e.target.value })} />
                <label className="check">
                  <input type="checkbox" checked={item.required} onChange={(e) => update(index, { required: e.target.checked })} />
                  필수
                </label>
                <button className="button button-danger" type="button"
                  onClick={() => setItems((list) => list.filter((_, i) => i !== index))}>삭제</button>
              </div>
            ))}
            <button className="button" type="button"
              onClick={() => setItems((list) => [...list, { key: '', label: '', required: true }])}>항목 추가</button>
          </fieldset>

          {message && <p className="alert" role="alert">{message}</p>}
          {notice && <p className="notice" role="status">{notice}</p>}
          <button className="button button-primary" onClick={save}>저장</button>
        </div>
      )}
      {!saved && message && <p className="alert" role="alert">{message}</p>}
      <p className="helper"><Link to={`/orgs/${orgId}`}>기관으로 돌아가기</Link></p>
    </main>
  )
}
