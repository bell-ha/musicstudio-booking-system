import { useCallback, useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api'
import { won } from '../academy/format'
import { dateLabel } from '../practice/time'
import { METHOD_LABEL, stateBadge, type InvoiceDetail, type Method, type Payment } from './types'

type Mode = { kind: 'PAYMENT' | 'REFUND'; requestId: string } | null

/**
 * 청구서 시트 (UC-61): 금액·받은 돈·남은 돈, 장부(취소한 것도 지우지 않고 줄 그어 보여 줌), 입금·환불 기록, 취소, 무효.
 * 입금 폼을 열 때 requestId를 한 번 만든다. 저장을 두 번 누르거나 응답을 못 받아 다시 눌러도 같은 번호라
 * 서버가 한 번만 기록한다(ADR 0016). 폼을 닫았다 열면 새 번호다.
 */
export function InvoiceSheet({ orgId, invoiceId, today, onChanged, onClose }: {
  orgId: string
  invoiceId: number
  today: string
  onChanged: () => void
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [detail, setDetail] = useState<InvoiceDetail>()
  const [mode, setMode] = useState<Mode>(null)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState<Method>('TRANSFER')
  const [paidOn, setPaidOn] = useState(today)
  const [memo, setMemo] = useState('')
  const [voiding, setVoiding] = useState<'invoice' | number | null>(null)
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState('')
  const base = `/organizations/${orgId}/billing`

  const load = useCallback(() => {
    api<InvoiceDetail>(`${base}/invoices/${invoiceId}`).then(setDetail).catch((e) => setMessage(errorMessage(e)))
  }, [base, invoiceId])

  useEffect(() => {
    dialog.current?.showModal()
    load()
  }, [load])

  function open(kind: 'PAYMENT' | 'REFUND') {
    setMode({ kind, requestId: crypto.randomUUID() })
    setAmount(String(kind === 'PAYMENT' ? detail!.invoice.balance : detail!.invoice.paid))
    setMessage('')
  }

  async function record() {
    setMessage('')
    try {
      await api(`${base}/invoices/${invoiceId}/payments`, {
        method: 'POST',
        body: { requestId: mode!.requestId, kind: mode!.kind, method, amount: Number(amount), paidOn, memo: memo.trim() || null },
      })
      setMode(null)
      setMemo('')
      load()
      onChanged()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  async function voidIt() {
    if (!reason.trim()) {
      setMessage('사유를 적어 주세요.')
      return
    }
    try {
      await api(voiding === 'invoice' ? `${base}/invoices/${invoiceId}/void` : `${base}/payments/${voiding}/void`,
        { method: 'POST', body: { reason } })
      setVoiding(null)
      setReason('')
      load()
      onChanged()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  const inv = detail?.invoice
  const badge = inv && stateBadge(inv)
  const live = (p: Payment) => !p.voided

  return (
    <dialog ref={dialog} className="sheet" onClose={onClose} onClick={(e) => e.target === dialog.current && dialog.current?.close()}>
      <div className="sheet-form">
        <header className="sheet-header">
          <div>
            <p className="card-title">{inv ? `${inv.studentName} · ${inv.title}` : '청구서'}</p>
            {inv && <p className="card-meta tnum">기한 {dateLabel(inv.dueDate)} {badge && <span className={badge.className}>{badge.text}</span>}</p>}
          </div>
          <button type="button" className="shell-link" aria-label="닫기" onClick={() => dialog.current?.close()}><X size={20} /></button>
        </header>
        <div className="sheet-body form">
          {inv && (
            <dl className="money">
              <div><dt>청구</dt><dd className="tnum">{won(inv.amount)}</dd></div>
              <div><dt>받음</dt><dd className="tnum">{won(inv.paid)}</dd></div>
              <div><dt>남음</dt><dd className="tnum money-due">{won(inv.balance)}</dd></div>
            </dl>
          )}
          {inv?.state === 'VOID' && <p className="notice-inline">무효로 한 청구서예요. 사유: {inv.voidReason}</p>}

          {mode && (
            <div className="card-inset form">
              <p className="card-title">{mode.kind === 'PAYMENT' ? '입금 기록' : '환불 기록'}</p>
              <div className="field">
                <label htmlFor="pay-amount">금액 (원)</label>
                <input id="pay-amount" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))} />
              </div>
              <fieldset className="segmented" aria-label="방법">
                {(Object.keys(METHOD_LABEL) as Method[]).map((m) => (
                  <label key={m} className={method === m ? 'segment segment-on' : 'segment'}>
                    <input type="radio" name="method" checked={method === m} onChange={() => setMethod(m)} />
                    {METHOD_LABEL[m]}
                  </label>
                ))}
              </fieldset>
              <div className="form-row wrap">
                <input type="date" aria-label="받은 날" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
                <input aria-label="메모" placeholder="메모 (선택)" maxLength={200} value={memo} onChange={(e) => setMemo(e.target.value)} />
              </div>
              <div className="actions">
                <button type="button" className="button button-primary" onClick={record} disabled={!Number(amount)}>
                  {won(Number(amount) || 0)} {mode.kind === 'PAYMENT' ? '받음' : '돌려줌'}으로 기록
                </button>
                <button type="button" className="button" onClick={() => setMode(null)}>취소</button>
              </div>
            </div>
          )}

          {detail && detail.payments.length > 0 && (
            <>
              <h2 className="group-title">장부</h2>
              <ul className="group">
                {detail.payments.map((p) => (
                  <li key={p.id} className={p.voided ? 'row row-compact row-muted' : 'row row-compact'}>
                    <span className="row-text">
                      <span className={p.voided ? 'row-title strike' : 'row-title'}>
                        {p.kind === 'REFUND' ? '환불 −' : ''}{won(p.amount)}
                      </span>
                      <span className="row-meta">
                        {dateLabel(p.paidOn)} · {METHOD_LABEL[p.method]} · {p.recordedBy}
                        {p.memo && ` · ${p.memo}`}{p.voided && ` · 취소: ${p.voidReason}`}
                      </span>
                    </span>
                    {live(p) && p.kind === 'PAYMENT' && (
                      <Link className="link-button link-plain" to={`/orgs/${orgId}/billing/receipts/${p.id}`}>영수증</Link>
                    )}
                    {live(p) && <button type="button" className="link-button" onClick={() => { setVoiding(p.id); setReason('') }}>취소</button>}
                  </li>
                ))}
              </ul>
            </>
          )}

          {voiding !== null && (
            <div className="card-inset form-row wrap">
              <input aria-label="사유" placeholder={voiding === 'invoice' ? '무효 사유 (예: 금액을 잘못 적음)' : '취소 사유 (예: 금액 잘못 적음)'}
                value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
              <button type="button" className="button button-danger" onClick={voidIt}>{voiding === 'invoice' ? '청구서 무효' : '기록 취소'}</button>
              <button type="button" className="button" onClick={() => setVoiding(null)}>그만두기</button>
            </div>
          )}
          {message && <p className="alert" role="alert">{message}</p>}
          {inv && inv.state !== 'VOID' && inv.paid === 0 && voiding === null && (
            <button type="button" className="link-button" onClick={() => { setVoiding('invoice'); setReason('') }}>청구서 무효로 하기</button>
          )}
        </div>
        {inv && inv.state !== 'VOID' && !mode && (
          <footer className="sheet-footer">
            {inv.balance > 0 && <button type="button" className="button button-primary" onClick={() => open('PAYMENT')}>입금 기록</button>}
            {inv.paid > 0 && <button type="button" className="button" onClick={() => open('REFUND')}>환불 기록</button>}
          </footer>
        )}
      </div>
    </dialog>
  )
}
