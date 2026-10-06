import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import { api, errorMessage } from '../api'
import { dateLabel } from '../practice/time'
import { conflictText, DAY_SHORT, DAYS } from './lessons'
import type { EnrollmentDetail, Slot } from './types'

/**
 * UC-49 고정 일정 정하기·바꾸기. 주 1~3회, 요일 칩 + 시각(5분 단위).
 * 바꾸면 적용 시작일 이후의 출결 안 한 회차만 다시 만든다(지난 회차·출결한 회차는 그대로).
 * 겹치면 서버가 409와 겹치는 날짜를 주고, 아무것도 바뀌지 않는다.
 */
export function ScheduleEditor({ orgId, enrollment, today, onSaved, onCancel }: {
  orgId: string
  enrollment: EnrollmentDetail
  today: string
  onSaved: (notice: string) => void
  onCancel: () => void
}) {
  const [slots, setSlots] = useState<Slot[]>(
    enrollment.schedule.length > 0 ? enrollment.schedule.map((s) => ({ ...s, startTime: s.startTime.slice(0, 5) }))
      : [{ dayOfWeek: 'MONDAY', startTime: '16:00' }])
  const [from, setFrom] = useState(today > enrollment.startsOn ? today : enrollment.startsOn)
  const [message, setMessage] = useState('')

  const set = (i: number, patch: Partial<Slot>) => setSlots(slots.map((s, j) => (j === i ? { ...s, ...patch } : s)))

  async function save() {
    setMessage('')
    try {
      const r = await api<{ created: number; firstDate: string | null; lastDate: string | null }>(
        `/organizations/${orgId}/academy/enrollments/${enrollment.id}/schedule`,
        { method: 'PUT', body: { version: enrollment.version, from, slots } })
      onSaved(enrollment.status === 'PAUSED'
        ? '일정을 저장했어요. 다시 시작하면 이 일정으로 회차가 만들어져요.'
        : r.created > 0
          ? `회차 ${r.created}개를 만들었어요 (${dateLabel(r.firstDate!)} ~ ${dateLabel(r.lastDate!)}).`
          : '일정을 저장했어요.')
    } catch (error) {
      setMessage(conflictText(error) ?? errorMessage(error))
    }
  }

  return (
    <div className="card-inset form">
      <p className="card-title">고정 일정</p>
      {slots.map((s, i) => (
        <div key={i} className="slot-row">
          <div className="day-chips" role="radiogroup" aria-label={`${i + 1}번째 요일`}>
            {DAYS.map((d) => (
              <button key={d} type="button" role="radio" aria-checked={s.dayOfWeek === d}
                className={s.dayOfWeek === d ? 'day-chip day-chip-on' : 'day-chip'} onClick={() => set(i, { dayOfWeek: d })}>
                {DAY_SHORT[d]}
              </button>
            ))}
          </div>
          <input type="time" step={300} value={s.startTime} aria-label={`${i + 1}번째 시각`} className="input-time"
            onChange={(e) => set(i, { startTime: e.target.value })} />
          {slots.length > 1 && (
            <button type="button" className="shell-link" aria-label="빼기" onClick={() => setSlots(slots.filter((_, j) => j !== i))}>
              <X size={18} />
            </button>
          )}
        </div>
      ))}
      {slots.length < 3 && (
        <button type="button" className="button" onClick={() => setSlots([...slots, { dayOfWeek: 'THURSDAY', startTime: slots[0].startTime }])}>
          <Plus size={16} /> 요일 더하기 (주 {slots.length + 1}회)
        </button>
      )}
      <div className="field">
        <label htmlFor={`from-${enrollment.id}`}>적용 시작일</label>
        <input id={`from-${enrollment.id}`} type="date" value={from} min={enrollment.startsOn} onChange={(e) => setFrom(e.target.value)} />
      </div>
      <p className="hint">레슨 길이는 상품을 따라요. 이미 지난 회차와 출결을 남긴 회차는 바뀌지 않아요.</p>
      {message && <p className="alert" role="alert">{message}</p>}
      <div className="actions">
        <button type="button" className="button button-primary" onClick={save}>저장</button>
        <button type="button" className="button" onClick={onCancel}>취소</button>
      </div>
    </div>
  )
}
