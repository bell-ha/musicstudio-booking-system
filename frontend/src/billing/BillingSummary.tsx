import { useEffect, useState } from 'react'
import { ChevronRight, Wallet } from 'lucide-react'
import { Link } from 'react-router'
import { api } from '../api'
import { won } from '../academy/format'
import type { Listing } from './types'

/** 기관 홈 "수납" 한 줄 (관리자): 미납 총액과 기한 지난 수. 누르면 수납 화면 */
export function BillingSummary({ orgId }: { orgId: string }) {
  const [listing, setListing] = useState<Listing>()
  useEffect(() => {
    api<Listing>(`/organizations/${orgId}/billing/invoices?state=OVERDUE`).then(setListing).catch(() => {})
  }, [orgId])
  if (!listing) return null
  return (
    <section>
      <h2 className="group-title">수납</h2>
      <ul className="group">
        <li>
          <Link className="row" to="billing">
            <span className={listing.overdueCount > 0 ? 'row-icon row-icon-warn' : 'row-icon'}><Wallet size={20} /></span>
            <span className="row-text">
              <span className="row-title">미납 {won(listing.outstanding)}</span>
              <span className="row-meta">{listing.overdueCount > 0 ? `기한 지난 청구서 ${listing.overdueCount}건` : '기한 지난 청구서 없음'}</span>
            </span>
            <ChevronRight className="row-chevron" size={20} />
          </Link>
        </li>
      </ul>
    </section>
  )
}
