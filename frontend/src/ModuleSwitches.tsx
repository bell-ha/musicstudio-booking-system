import { MODULES } from './modules'

/** 쓰는 기능 스위치 묶음 (UC-02 기관 만들기, UC-03 기관 설정) */
export function ModuleSwitches({ value, onToggle }: { value: string[]; onToggle: (key: string, on: boolean) => void }) {
  return (
    <ul className="group">
      {MODULES.map((m) => {
        const on = value.includes(m.key)
        return (
          <li key={m.key} className="room-row">
            <div className="row">
              <span className="row-text">
                <span className="row-title">{m.title}</span>
                <span className="row-meta row-meta-wrap">{m.meta}</span>
              </span>
            </div>
            <button type="button" role="switch" aria-checked={on} aria-label={`${m.title} 쓰기`} className="switch"
              disabled={m.key === 'BILLING' && !value.includes('ACADEMY')} onClick={() => onToggle(m.key, !on)}>
              <span className="switch-knob" />
            </button>
          </li>
        )
      })}
    </ul>
  )
}
