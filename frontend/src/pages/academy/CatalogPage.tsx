import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../../api'
import { productTerms } from '../../academy/format'
import type { Catalog, Product, ProductKind } from '../../academy/types'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-40. 과목과 수업 상품. 상품의 이름·가격만 고칠 수 있고, 이미 등록된 수강의 금액은 바뀌지 않는다 */
export function CatalogPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [kind, setKind] = useState<ProductKind>('PERIOD')
  const [editing, setEditing] = useState<number | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    let current = true
    api<Catalog>(`/organizations/${orgId}/academy/catalog`)
      .then((c) => current && setCatalog(c))
      .catch((error) => current && setMessage(errorMessage(error)))
    return () => { current = false }
  }, [me, orgId, refresh])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  async function send(request: Promise<unknown>, form?: HTMLFormElement) {
    setErrors({})
    setMessage('')
    try {
      await request
      form?.reset()
      setEditing(null)
      setRefresh((v) => v + 1)
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  function addSubject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    send(api(`/organizations/${orgId}/academy/subjects`, { method: 'POST', body: { name: new FormData(form).get('subjectName') } }), form)
  }

  function addProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const f = new FormData(form)
    const count = Number(f.get('count'))
    send(api(`/organizations/${orgId}/academy/products`, {
      method: 'POST',
      body: {
        subjectId: Number(f.get('subjectId')),
        name: f.get('productName'),
        kind,
        sessionCount: kind === 'COUNT' ? count : null,
        periodMonths: kind === 'PERIOD' ? count : null,
        lessonMinutes: Number(f.get('lessonMinutes')),
        price: Number(f.get('price')),
      },
    }), form)
  }

  function updateProduct(p: Product, event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const f = new FormData(event.currentTarget)
    send(api(`/organizations/${orgId}/academy/products/${p.id}`, {
      method: 'PATCH',
      body: { name: f.get('name'), price: Number(f.get('price')) },
    }))
  }

  const subjects = catalog?.subjects ?? []

  return (
    <main className="page">
      <h1>과목·상품</h1>
      {message && <p className="alert" role="alert">{message}</p>}

      {subjects.map((s) => (
        <section key={s.id} className="section">
          <p className="card-title">{s.name}</p>
          <ul className="list">
            {catalog!.products.filter((p) => p.subjectId === s.id).map((p) => (
              <li key={p.id} className="card">
                {editing === p.id ? (
                  <form className="form" onSubmit={(e) => updateProduct(p, e)}>
                    <Field id={`name-${p.id}`} label="상품 이름"><input id={`name-${p.id}`} name="name" defaultValue={p.name} required /></Field>
                    <Field id={`price-${p.id}`} label="가격 (원)"><input id={`price-${p.id}`} name="price" type="number" min={0} defaultValue={p.price} required /></Field>
                    <p className="card-meta">가격을 바꿔도 이미 등록된 수강의 금액은 그대로예요.</p>
                    <div className="actions">
                      <button className="button">저장</button>
                      <button className="button" type="button" onClick={() => setEditing(null)}>취소</button>
                    </div>
                  </form>
                ) : (
                  <>
                    <p className="card-title">{p.name}</p>
                    <p className="card-meta tnum">{productTerms(p)}</p>
                    <div className="actions"><button className="button" onClick={() => setEditing(p.id)}>수정</button></div>
                  </>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
      {catalog && subjects.length === 0 && <p className="card empty">과목을 먼저 추가해 주세요. (예: 피아노, 보컬)</p>}

      <form className="form card section" onSubmit={addSubject}>
        <p className="card-title">과목 추가</p>
        <Field id="subjectName" label="과목 이름" error={errors.name}>
          <input id="subjectName" name="subjectName" placeholder="예: 피아노" required />
        </Field>
        <button className="button">과목 추가</button>
      </form>

      {subjects.length > 0 && (
        <form className="form card section" onSubmit={addProduct}>
          <p className="card-title">상품 추가</p>
          <Field id="subjectId" label="과목">
            <select id="subjectId" name="subjectId">{subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          </Field>
          <Field id="productName" label="상품 이름" error={errors.name}>
            <input id="productName" name="productName" placeholder="예: 피아노 주 1회 3개월" required />
          </Field>
          <div className="form-row">
            <label className="check"><input type="radio" checked={kind === 'PERIOD'} onChange={() => setKind('PERIOD')} />기간권</label>
            <label className="check"><input type="radio" checked={kind === 'COUNT'} onChange={() => setKind('COUNT')} />횟수권</label>
          </div>
          <Field id="count" label={kind === 'PERIOD' ? '기간 (개월)' : '횟수 (회)'}>
            <input id="count" name="count" type="number" min={1} defaultValue={kind === 'PERIOD' ? 3 : 10} key={kind} required />
          </Field>
          <Field id="lessonMinutes" label="레슨 길이 (분)" error={errors.lessonMinutes}>
            <input id="lessonMinutes" name="lessonMinutes" type="number" min={1} defaultValue={50} required />
          </Field>
          <Field id="price" label="가격 (원)" error={errors.price}>
            <input id="price" name="price" type="number" min={0} step={1000} required />
          </Field>
          <button className="button button-primary">상품 추가</button>
        </form>
      )}
      <p className="helper"><Link to={`/orgs/${orgId}`}>기관으로 돌아가기</Link></p>
    </main>
  )
}
