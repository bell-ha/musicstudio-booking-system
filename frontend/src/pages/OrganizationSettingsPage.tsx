import { useState } from 'react'
import { useParams } from 'react-router'
import { api, errorMessage } from '../api'
import { Field } from '../Field'
import { TYPE_LABEL } from '../labels'
import { useOrgSite } from '../orgSite'
import { useMyOrganization } from '../useMyOrganization'
import { NotMember } from './OrganizationHomePage'

const MODULES = [
  { key: 'PRACTICE_ROOM', title: '연습실', meta: '평면도, 방, 예약 정책, 지도에서 예약' },
  { key: 'ACADEMY', title: '학원 관리', meta: '원생, 수강, 레슨 일정·출결, 레슨 기록' },
  { key: 'BILLING', title: '청구·결제', meta: '청구서, 입금·환불 기록, 미납, 영수증 (학원 관리 필요)' },
] as const

/**
 * UC-03 기관 설정 (소유자). 이름과 쓰는 기능(모듈). 기능을 꺼도 데이터는 지우지 않고, 다시 켜면 그대로다.
 * 시간대는 만들 때 정하고 바꾸지 않는다(이미 만든 예약·레슨의 날짜가 어긋난다).
 */
export function OrganizationSettingsPage() {
  const { reloadOrg } = useOrgSite()
  const [version, setVersion] = useState(0)
  const { orgId } = useParams()
  const org = useMyOrganization(orgId, version)
  const [name, setName] = useState<string>()
  const [modules, setModules] = useState<string[]>()
  const [message, setMessage] = useState('')
  const [saved, setSaved] = useState('')

  if (org === undefined) return <main className="page page-wide" />
  if (org === null || org.role !== 'OWNER') return <NotMember />
  const shownName = name ?? org.name
  const shownModules = modules ?? org.modules

  async function save() {
    setMessage('')
    setSaved('')
    try {
      await api(`/organizations/${org!.organizationId}`, { method: 'PATCH', body: { name: shownName, modules: shownModules } })
      setSaved('저장했어요. 메뉴가 바뀐 기능에 맞춰졌어요.')
      setName(undefined)
      setModules(undefined)
      setVersion((v) => v + 1)
      reloadOrg()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  function toggle(key: string, on: boolean) {
    if (!on && !window.confirm('이 기능을 끌까요? 메뉴와 화면에서 사라지지만 데이터는 남아 있고, 다시 켜면 그대로 돌아와요.')) return
    // 청구는 원생·수강에 붙어서 학원 관리 없이 켤 수 없다. 학원 관리를 끄면 청구·결제도 같이 끈다 (서버는 422)
    const next = on ? [...shownModules, key] : shownModules.filter((m) => m !== key && !(key === 'ACADEMY' && m === 'BILLING'))
    setModules(next)
  }

  return (
    <main className="page page-wide">
      <h1>기관 설정</h1>
      <p className="page-sub">{TYPE_LABEL[org.type]} · 시간대 {org.timezone}</p>
      {saved && <p className="notice-inline" role="status">{saved}</p>}
      {message && <p className="alert" role="alert">{message}</p>}

      <section className="card form section">
        <Field id="org-name" label="기관 이름">
          <input id="org-name" maxLength={50} value={shownName} onChange={(e) => setName(e.target.value)} />
        </Field>
      </section>

      <h2 className="group-title">쓰는 기능</h2>
      <ul className="group">
        {MODULES.map((m) => {
          const on = shownModules.includes(m.key)
          return (
            <li key={m.key} className="room-row">
              <div className="row">
                <span className="row-text">
                  <span className="row-title">{m.title}</span>
                  <span className="row-meta">{m.meta}</span>
                </span>
              </div>
              <button type="button" role="switch" aria-checked={on} aria-label={`${m.title} 쓰기`} className="switch"
                disabled={m.key === 'BILLING' && !shownModules.includes('ACADEMY')} onClick={() => toggle(m.key, !on)}>
                <span className="switch-knob" />
              </button>
            </li>
          )
        })}
      </ul>
      <p className="hint section">시간대는 만들 때 정해요. 이미 잡힌 예약과 레슨의 날짜가 어긋나서 바꿀 수 없어요.</p>

      <button className="button button-primary button-block section" onClick={save}>저장</button>
    </main>
  )
}
