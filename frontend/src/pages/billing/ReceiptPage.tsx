import { useEffect, useState } from 'react'
import { Printer } from 'lucide-react'
import { useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { won } from '../../academy/format'
import { METHOD_LABEL, type Method } from '../../billing/types'
import { useOrgSite } from '../../orgSite'
import { dateLabel } from '../../practice/time'

type Receipt = { number: string; organizationName: string; studentName: string; title: string; amount: number; method: Method; paidOn: string }

/**
 * UC-63 영수증. 흰 종이 모양을 화면에 보여 주거나 브라우저 인쇄(@media print에서 앱 틀을 숨김).
 * 세법상 영수증(현금영수증·세금계산서)이 아니라고 적는다. 기관 로고·주소·전화는 사이트 정보에서.
 */
export function ReceiptPage() {
  const { orgId, paymentId } = useParams()
  const { site } = useOrgSite()
  const [receipt, setReceipt] = useState<Receipt>()
  const [message, setMessage] = useState('')

  useEffect(() => {
    api<Receipt>(`/organizations/${orgId}/billing/payments/${paymentId}/receipt`).then(setReceipt).catch((e) => setMessage(errorMessage(e)))
  }, [orgId, paymentId])

  if (message) return <main className="page"><p className="alert" role="alert">{message}</p></main>
  if (!receipt) return <main className="page" />

  return (
    <main className="page">
      <div className="receipt">
        <header className="receipt-head">
          {site?.logoUrl && <img src={site.logoUrl} alt="" className="receipt-logo" />}
          <div>
            <p className="receipt-org">{receipt.organizationName}</p>
            <p className="card-meta">{[site?.address, site?.phone].filter(Boolean).join(' · ')}</p>
          </div>
        </header>
        <h1 className="receipt-title">영수증</h1>
        <dl className="receipt-lines">
          <div><dt>번호</dt><dd className="tnum">{receipt.number}</dd></div>
          <div><dt>받는 분</dt><dd>{receipt.studentName}</dd></div>
          <div><dt>항목</dt><dd>{receipt.title}</dd></div>
          <div><dt>받은 날</dt><dd className="tnum">{dateLabel(receipt.paidOn)}</dd></div>
          <div><dt>방법</dt><dd>{METHOD_LABEL[receipt.method]}</dd></div>
          <div className="receipt-total"><dt>금액</dt><dd className="tnum">{won(receipt.amount)}</dd></div>
        </dl>
        <p className="receipt-note">위 금액을 받았습니다. 이 영수증은 세법상 영수증(현금영수증·세금계산서)이 아닙니다.</p>
      </div>
      <button type="button" className="button button-primary button-block no-print" onClick={() => window.print()}>
        <Printer size={18} /> 인쇄
      </button>
    </main>
  )
}
