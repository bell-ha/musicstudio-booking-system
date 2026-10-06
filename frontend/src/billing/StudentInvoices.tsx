import { useEffect, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import { api } from '../api'
import { won } from '../academy/format'
import { dateLabel } from '../practice/time'
import { InvoiceSheet } from './InvoiceSheet'
import { stateBadge, type Listing } from './types'

/** 원생 상세의 "청구" 묶음 (관리자, BILLING 켜진 기관). 행을 누르면 청구서 시트 */
export function StudentInvoices({ orgId, studentId, today }: { orgId: string; studentId: number; today: string }) {
  const [listing, setListing] = useState<Listing>()
  const [open, setOpen] = useState<number | null>(null)
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    let current = true
    api<Listing>(`/organizations/${orgId}/billing/invoices?studentId=${studentId}`)
      .then((l) => current && setListing(l)).catch(() => {})
    return () => { current = false }
  }, [orgId, studentId, refresh])

  if (!listing) return null
  return (
    <section>
      <h2 className="section-title">청구 {listing.outstanding > 0 && <span className="row-sub">· 미납 {won(listing.outstanding)}</span>}</h2>
      {listing.invoices.length === 0 && <p className="card empty">청구서가 없어요. 수강을 등록하면 자동으로 생겨요.</p>}
      <ul className="group">
        {listing.invoices.map((i) => {
          const badge = stateBadge(i)
          return (
            <li key={i.id}>
              <button type="button" className="row row-button" onClick={() => setOpen(i.id)}>
                <span className="row-text">
                  <span className="row-title">{i.title} <span className={badge.className}>{badge.text}</span></span>
                  <span className="row-meta">기한 {dateLabel(i.dueDate)} · 청구 {won(i.amount)}</span>
                </span>
                <span className="row-amount tnum">{won(i.balance)}</span>
                <ChevronRight className="row-chevron" size={20} />
              </button>
            </li>
          )
        })}
      </ul>
      {open !== null && <InvoiceSheet orgId={orgId} invoiceId={open} today={today}
        onChanged={() => setRefresh((v) => v + 1)} onClose={() => setOpen(null)} />}
    </section>
  )
}
