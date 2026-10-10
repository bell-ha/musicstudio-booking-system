import { useEffect, useState } from 'react'
import { ChevronRight, Circle, CircleCheck } from 'lucide-react'
import { Link } from 'react-router'
import { api } from '../api'
import type { Catalog, StudentSummary } from '../academy/types'
import type { MyOrganization } from '../labels'
import type { FloorsResponse } from '../practice/types'
import type { Site } from '../site/site'
import { setupSteps, type SetupData } from './setupSteps'

const storageKey = (orgId: number) => `madi.setup-hidden.${orgId}`

// 기기별 편의 상태라 잃어도(사생활 모드, 다른 기기) 다시 계산해서 보여 줄 뿐이다
function readHidden(orgId: number) {
  try { return localStorage.getItem(storageKey(orgId)) !== null } catch { return false }
}
function writeHidden(orgId: number, value: 'hidden' | 'done') {
  try { localStorage.setItem(storageKey(orgId), value) } catch { /* 이번 화면에서만 숨긴다 */ }
}

/**
 * UC-15 시작하기 (관리자 홈). 켠 모듈의 목록 API를 동시에 불러 단계 완료를 계산한다.
 * 하나라도 실패하면 틀린 "미완료"를 보이느니 그리지 않는다. 다 끝나면 "done"을 적고 다음부터 부르지 않는다.
 */
export function SetupChecklist({ org, site }: { org: MyOrganization; site: Site | undefined }) {
  const id = org.organizationId
  const [hidden, setHidden] = useState(() => readHidden(id))
  const [data, setData] = useState<Omit<SetupData, 'siteDecorated'>>()

  useEffect(() => {
    if (hidden) return
    let current = true
    const base = `/organizations/${id}`
    const academy = org.modules.includes('ACADEMY')
    const practice = org.modules.includes('PRACTICE_ROOM')
    Promise.all([
      academy ? api<Catalog>(`${base}/academy/catalog`) : undefined,
      academy ? api<StudentSummary[]>(`${base}/academy/students`) : undefined,
      practice ? api<FloorsResponse>(`${base}/practice/floors`) : undefined,
      api<{ role: string }[]>(`${base}/members?status=ACTIVE`),
    ]).then(([catalog, students, floors, members]) => {
      if (!current) return
      setData({
        products: catalog?.products.length,
        students: students?.length,
        enrolled: students?.filter((s) => s.enrollments.length > 0).length,
        placedRooms: floors?.floors.reduce((n, f) => n + f.rooms.length, 0),
        people: members.filter((m) => m.role === 'TEACHER' || m.role === 'STUDENT').length,
      })
    }).catch(() => {})
    return () => { current = false }
  }, [id, org.modules, hidden])

  const steps = data && site !== undefined
    ? setupSteps(org.modules, { ...data, siteDecorated: !!site.logoUrl || site.intro.trim() !== '' })
    : undefined
  const finished = !!steps && steps.every((s) => s.done)

  useEffect(() => {
    if (finished) writeHidden(id, 'done')
  }, [finished, id])

  if (hidden || !steps || finished) return null
  const next = steps.find((s) => !s.done)

  return (
    <section>
      <h2 className="group-title">시작하기 · <span className="tnum">{steps.filter((s) => s.done).length}/{steps.length}</span></h2>
      <ul className="group">
        {steps.map((s) => (
          <li key={s.key}>
            {s.done ? (
              <div className="row row-compact">
                <span className="row-icon row-icon-done"><CircleCheck size={18} /></span>
                <span className="row-text"><span className="row-title-plain">{s.title}</span></span>
                <span className="badge badge-done">완료</span>
              </div>
            ) : (
              <Link className="row" to={s.to}>
                <span className="row-icon row-icon-muted"><Circle size={18} /></span>
                <span className="row-text">
                  <span className="row-title">{s.title}{s === next && <span className="badge badge-next">다음</span>}</span>
                  <span className="row-meta row-meta-wrap">{s.meta}</span>
                </span>
                <ChevronRight className="row-chevron" size={20} />
              </Link>
            )}
          </li>
        ))}
        <li>
          <button type="button" className="row row-compact row-button setup-hide" onClick={() => { writeHidden(id, 'hidden'); setHidden(true) }}>
            <span className="row-icon" aria-hidden="true" />
            <span className="row-text"><span className="row-meta">시작하기 숨기기</span></span>
          </button>
        </li>
      </ul>
    </section>
  )
}
