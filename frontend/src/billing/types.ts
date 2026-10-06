// 수납 API 계약 (02-API 56~63). 금액은 원 단위 정수
export type InvoiceState = 'UNPAID' | 'PARTIAL' | 'PAID' | 'VOID'
export type Method = 'CASH' | 'TRANSFER' | 'CARD' | 'OTHER'

export type Invoice = {
  id: number
  studentId: number
  studentName: string
  enrollmentId: number | null
  auto: boolean
  title: string
  amount: number
  paid: number
  balance: number
  dueDate: string
  state: InvoiceState
  overdue: boolean
  voidReason: string | null
}

export type Payment = {
  id: number
  invoiceId: number
  kind: 'PAYMENT' | 'REFUND'
  method: Method
  amount: number
  paidOn: string
  memo: string | null
  recordedBy: string
  voided: boolean
  voidReason: string | null
}

export type Listing = { outstanding: number; overdueCount: number; invoices: Invoice[] }
export type InvoiceDetail = { invoice: Invoice; payments: Payment[] }

export const METHOD_LABEL: Record<Method, string> = { CASH: '현금', TRANSFER: '계좌이체', CARD: '카드', OTHER: '기타' }
export const STATE_LABEL: Record<InvoiceState, string> = { UNPAID: '미납', PARTIAL: '일부 납부', PAID: '완납', VOID: '무효' }

/** 상태 배지: 색만이 아니라 글자로도 (DESIGN 2절) */
export function stateBadge(i: Invoice): { text: string; className: string } {
  if (i.overdue) return { text: '기한 지남', className: 'badge badge-warning' }
  if (i.state === 'PAID') return { text: '완납', className: 'badge badge-done' }
  if (i.state === 'VOID') return { text: '무효', className: 'badge badge-unavailable' }
  return { text: STATE_LABEL[i.state], className: 'badge' }
}
