import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api, errorMessage, fieldErrors } from '../../api'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { DAY_LABEL, DAYS, type Day, type FloorsResponse, type Policy } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/** UC-22. 정책은 통째로 저장한다 (PUT). 검증은 서버가 하고, 오류는 해당 칸 아래에 보여 준다 */
export function PolicyPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [policy, setPolicy] = useState<Policy | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    api<FloorsResponse>(`/organizations/${orgId}/practice/floors`)
      .then((data) => setPolicy(data.policy))
      .catch((error) => setMessage(errorMessage(error)))
  }, [me, orgId])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  const set = (patch: Partial<Policy>) => setPolicy((p) => p && { ...p, ...patch })
  const setHours = (day: Day, value: [string, string] | null) => setPolicy((p) => p && { ...p, hours: { ...p.hours, [day]: value } })

  async function save() {
    setErrors({})
    setMessage('')
    setNotice('')
    try {
      setPolicy(await api<Policy>(`/organizations/${orgId}/practice/policy`, { method: 'PUT', body: policy }))
      setNotice('저장했어요. 새 예약부터 적용돼요.')
    } catch (error) {
      setErrors(fieldErrors(error))
      setMessage(errorMessage(error))
    }
  }

  const minutes = (id: keyof Policy, label: string, step: number) => (
    <Field id={id} label={label} error={errors[id]}>
      <input id={id} type="number" min={0} step={step} value={policy![id] as number}
        onChange={(e) => set({ [id]: Number(e.target.value) })} />
    </Field>
  )

  return (
    <main className="page">
      <h1>예약 정책</h1>
      {policy && (
        <div className="form">
          <fieldset className="card fieldset">
            <legend className="card-title">운영 시간</legend>
            {DAYS.map((day) => {
              const hours = policy.hours[day]
              return (
                <div key={day}>
                  <div className="form-row">
                    <span className="day-label">{DAY_LABEL[day]}</span>
                    <label className="check">
                      <input type="checkbox" aria-label={`${DAY_LABEL[day]}요일 운영`} checked={hours !== null}
                        onChange={(e) => setHours(day, e.target.checked ? ['09:00', '22:00'] : null)} />
                    </label>
                    {!hours && <span className="card-meta">휴무</span>}
                    {hours && (
                      <>
                        <input type="time" aria-label={`${DAY_LABEL[day]} 시작`} step={policy.slotMinutes * 60} value={hours[0]}
                          onChange={(e) => setHours(day, [e.target.value, hours[1]])} />
                        ~
                        <input type="time" aria-label={`${DAY_LABEL[day]} 끝`} step={policy.slotMinutes * 60} value={hours[1]}
                          onChange={(e) => setHours(day, [hours[0], e.target.value])} />
                      </>
                    )}
                  </div>
                  {errors[`hours.${day}`] && <p className="field-error">{errors[`hours.${day}`]}</p>}
                </div>
              )
            })}
          </fieldset>

          <Field id="slotMinutes" label="시간 단위" error={errors.slotMinutes}>
            <select id="slotMinutes" value={policy.slotMinutes} onChange={(e) => set({ slotMinutes: Number(e.target.value) as 30 | 60 })}>
              <option value={30}>30분</option>
              <option value={60}>60분</option>
            </select>
          </Field>
          {minutes('maxContinuousMinutes', '1회 최대 이용 (분)', policy.slotMinutes)}
          {minutes('dailyMaxMinutes', '1인 하루 최대 이용 (분)', policy.slotMinutes)}
          {minutes('cancelDeadlineMinutes', '취소 마감 (시작 몇 분 전까지)', 5)}

          <fieldset className="card fieldset">
            <legend className="card-title">예약 오픈</legend>
            <label className="check">
              <input type="radio" name="openMode" checked={policy.open.mode === 'ROLLING'}
                onChange={() => set({ open: { mode: 'ROLLING', days: 14 } })} />
              항상 오늘부터 며칠까지 열기
            </label>
            {policy.open.mode === 'ROLLING' && (
              <Field id="openDays" label="오늘을 포함한 날 수" error={errors['open.days']}>
                <input id="openDays" type="number" min={1} max={365} value={policy.open.days}
                  onChange={(e) => set({ open: { mode: 'ROLLING', days: Number(e.target.value) } })} />
              </Field>
            )}
            <label className="check">
              <input type="radio" name="openMode" checked={policy.open.mode === 'WEEKLY'}
                onChange={() => set({ open: { mode: 'WEEKLY', dayOfWeek: 'FRIDAY', time: '09:00' } })} />
              매주 정해진 때에 다음 주(월~일) 열기
            </label>
            {policy.open.mode === 'WEEKLY' && (() => {
              const open = policy.open
              return (
                <div className="form-row">
                  <select aria-label="여는 요일" value={open.dayOfWeek}
                    onChange={(e) => set({ open: { ...open, dayOfWeek: e.target.value as Day } })}>
                    {DAYS.map((d) => <option key={d} value={d}>{DAY_LABEL[d]}요일</option>)}
                  </select>
                  <input type="time" aria-label="여는 시각" value={open.time}
                    onChange={(e) => set({ open: { ...open, time: e.target.value } })} />
                </div>
              )
            })()}
            {(errors['open.dayOfWeek'] || errors['open.time']) && (
              <p className="field-error">{errors['open.dayOfWeek'] ?? errors['open.time']}</p>
            )}
          </fieldset>

          {message && <p className="alert" role="alert">{message}</p>}
          {notice && <p className="notice" role="status">{notice}</p>}
          <button className="button button-primary" onClick={save}>저장</button>
        </div>
      )}
      {!policy && message && <p className="alert" role="alert">{message}</p>}
    </main>
  )
}
