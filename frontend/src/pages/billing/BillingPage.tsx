import { useEffect, useState, type FormEvent } from 'react'
import { ChevronRight } from 'lucide-react'
import { useParams, useSearchParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../../api'
import { won } from '../../academy/format'
import type { StudentSummary } from '../../academy/types'
import { InvoiceSheet } from '../../billing/InvoiceSheet'
import { stateBadge, type Listing } from '../../billing/types'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { dateLabel, todayIn, zoneOf } from '../../practice/time'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

const FILTERS = [['OPEN', '미납'], ['OVERDUE', '기한 지남'], ['PAID', '완납'], ['', '전체']] as const

/**
 * UC-60·62 수납. 머리에 미납 총액과 기한 지난 수, 칩으로 거르기, 행을 누르면 청구서 시트(입금·환불·취소).
 * 수강 등록 때 청구서가 자동으로 생기고(0원 제외), 교재비 같은 것은 여기서 직접 만든다.
 */
export function BillingPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [params, setParams] = useSearchParams()
  const filter = params.get('state') ?? 'OPEN'
  const [listing, setListing] = useState<Listing>()
  const [open, setOpen] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [students, setStudents] = useState<StudentSummary[]>([])
  const [refresh, setRefresh] = useState(0)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const tz = zoneOf(me?.timezone)

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    let current = true
    api<Listing>(`/organizations/${orgId}/billing/invoices?state=${filter}`)
      .then((l) => current && setListing(l)).catch((e) => current && setMessage(errorMessage(e)))
    return () => { current = false }
  }, [me, orgId, filter, refresh])

  useEffect(() => {
    if (creating && students.length === 0) api<StudentSummary[]>(`/organizations/${orgId}/academy/students`).then(setStudents).catch(() => {})
  }, [creating, orgId, students.length])

  if (me === undefined) return <main className="page page-wide" />
  if (me === null || !isManager(me.role)) return <NotMember />
  const today = todayIn(tz)

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const f = new FormData(event.currentTarget)
    setErrors({})
    try {
      await api(`/organizations/${orgId}/billing/invoices`, {
        method: 'POST',
        body: { studentId: Number(f.get('studentId')), title: f.get('title'), amount: Number(f.get('amount')), dueDate: f.get('dueDate') },
      })
      setCreating(false)
      setRefresh((v) => v + 1)
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  return (
    <main className="page page-wide">
      <div className="app-bar">
        <h1>수납</h1>
        {!creating && <button className="button button-primary" onClick={() => setCreating(true)}>청구서 만들기</button>}
      </div>

      {listing && (
        <dl className="money money-head">
          <div><dt>미납 총액</dt><dd className="tnum money-due">{won(listing.outstanding)}</dd></div>
          <div><dt>기한 지남</dt><dd className="tnum">{listing.overdueCount}건</dd></div>
        </dl>
      )}
      {message && <p className="alert" role="alert">{message}</p>}

      {creating && (
        <form className="card form section" onSubmit={create}>
          <p className="card-title">청구서 만들기</p>
          <p className="card-meta">수강을 등록하면 수강료 청구서는 자동으로 생겨요. 교재비·발표회비 같은 것을 여기서 만들어요.</p>
          <Field id="studentId" label="원생">
            <select id="studentId" name="studentId" required defaultValue="">
              <option value="" disabled>원생을 골라 주세요</option>
              {students.filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field id="title" label="항목" error={errors.title}><input id="title" name="title" maxLength={100} placeholder="예: 10월 교재비" required /></Field>
          <div className="form-row wrap">
            <Field id="amount" label="금액 (원)" error={errors.amount}><input id="amount" name="amount" inputMode="numeric" pattern="[0-9]*" required /></Field>
            <Field id="dueDate" label="기한"><input id="dueDate" name="dueDate" type="date" defaultValue={today} required /></Field>
          </div>
          <div className="actions">
            <button className="button button-primary">만들기</button>
            <button type="button" className="button" onClick={() => setCreating(false)}>취소</button>
          </div>
        </form>
      )}

      <div className="tabs section" role="tablist" aria-label="청구서 거르기">
        {FILTERS.map(([value, label]) => (
          <button key={value} type="button" role="tab" className="tab" aria-selected={filter === value}
            onClick={() => setParams(value ? { state: value } : { state: '' })}>{label}</button>
        ))}
      </div>

      {listing?.invoices.length === 0 && <p className="card empty">해당하는 청구서가 없어요.</p>}
      {listing && listing.invoices.length > 0 && (
        <ul className="group">
          {listing.invoices.map((i) => {
            const badge = stateBadge(i)
            return (
              <li key={i.id}>
                <button type="button" className="row row-button" onClick={() => setOpen(i.id)}>
                  <span className={i.state === 'VOID' ? 'avatar avatar-muted' : 'avatar'} aria-hidden="true">{i.studentName.slice(0, 1)}</span>
                  <span className="row-text">
                    <span className="row-title">{i.studentName} <span className={badge.className}>{badge.text}</span></span>
                    <span className="row-meta">{i.title} · 기한 {dateLabel(i.dueDate)}</span>
                  </span>
                  <span className="row-amount tnum">{i.state === 'PAID' || i.state === 'VOID' ? won(i.amount) : won(i.balance)}</span>
                  <ChevronRight className="row-chevron" size={20} />
                </button>
              </li>
            )
          })}
        </ul>
      )}

      {open !== null && (
        <InvoiceSheet orgId={orgId!} invoiceId={open} today={today}
          onChanged={() => setRefresh((v) => v + 1)} onClose={() => setOpen(null)} />
      )}
    </main>
  )
}
