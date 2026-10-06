import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { ChevronRight, DoorOpen, Wrench, X } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import type { FloorsResponse, Room } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

type Filter = 'ALL' | 'OPEN' | 'MAINTENANCE' | 'UNPLACED'
type Draft = { id?: number; name: string; capacity: number; equipment: string[] }

/**
 * UC-21. 층별 묶음 목록에서 상태를 한눈에 보고, 스위치로 예약을 받거나 멈춘다(점검 중).
 * 행을 누르면 시트에서 이름·인원·장비를 고친다. 방 추가도 같은 시트.
 */
export function RoomsPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [data, setData] = useState<FloorsResponse | null>(null)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [draft, setDraft] = useState<Draft | null>(null)
  const [message, setMessage] = useState('')

  const load = useCallback(() => api<FloorsResponse>(`/organizations/${orgId}/practice/floors`).then(setData), [orgId])

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    load().catch((error) => setMessage(errorMessage(error)))
  }, [me, load])

  if (me === undefined) return <main className="page page-wide" />
  if (me === null || !isManager(me.role)) return <NotMember />

  const all: Room[] = [...(data?.floors.flatMap((f) => f.rooms) ?? []), ...(data?.unplacedRooms ?? [])]
  const count = {
    ALL: all.length,
    OPEN: all.filter((r) => r.bookable).length,
    MAINTENANCE: all.filter((r) => !r.bookable).length,
    UNPLACED: data?.unplacedRooms.length ?? 0,
  }
  const pass = (r: Room) => filter === 'ALL' || (filter === 'OPEN' ? r.bookable
    : filter === 'MAINTENANCE' ? !r.bookable : r.floorId === null)
  const byName = (a: Room, b: Room) => a.name.localeCompare(b.name, 'ko', { numeric: true })
  const groups = [
    ...(data?.floors ?? []).map((f) => ({ key: `f${f.id}`, title: f.name, rooms: f.rooms.filter(pass).sort(byName) })),
    { key: 'unplaced', title: '평면도에 없음', rooms: (data?.unplacedRooms ?? []).filter(pass).sort(byName) },
  ].filter((g) => g.rooms.length > 0)

  async function send(request: Promise<unknown>) {
    setMessage('')
    try {
      await request
      setDraft(null)
      await load()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  function toggle(room: Room) {
    const text = room.bookable
      ? `${room.name}을(를) 점검 중으로 바꿀까요? 새 예약을 받지 않아요. 이미 있는 예약은 그대로 남아요.`
      : `${room.name}의 점검을 끝내고 예약을 다시 받을까요?`
    if (window.confirm(text)) {
      send(api(`/organizations/${orgId}/practice/rooms/${room.id}`, { method: 'PATCH', body: { bookable: !room.bookable } }))
    }
  }

  function save(d: Draft) {
    const body = { name: d.name, capacity: d.capacity, equipment: d.equipment }
    send(d.id
      ? api(`/organizations/${orgId}/practice/rooms/${d.id}`, { method: 'PATCH', body })
      : api(`/organizations/${orgId}/practice/rooms`, { method: 'POST', body }))
  }

  const filters: [Filter, string][] = [['ALL', '전체'], ['OPEN', '예약 받는 중'], ['MAINTENANCE', '점검 중'], ['UNPLACED', '평면도에 없음']]
  const newRoom = () => setDraft({ name: '', capacity: 1, equipment: [] })

  return (
    <main className="page page-wide">
      <div className="app-bar">
        <h1>방 관리</h1>
        <button className="button button-primary" onClick={newRoom}>방 추가</button>
      </div>
      {message && <p className="alert" role="alert">{message}</p>}

      <div className="tabs" role="tablist" aria-label="방 거르기">
        {filters.filter(([f]) => f === 'ALL' || count[f] > 0).map(([f, label]) => (
          <button key={f} type="button" role="tab" className="tab" aria-selected={filter === f} onClick={() => setFilter(f)}>
            {label} <span className="tab-count">{count[f]}</span>
          </button>
        ))}
      </div>

      {data && all.length === 0 && (
        <div className="card empty">
          <p>아직 방이 없어요.</p>
          <button className="button" onClick={newRoom}>첫 방 만들기</button>
        </div>
      )}

      <div className="groups">
        {groups.map((g) => (
          <section key={g.key}>
            <h2 className="group-title">{g.title} · {g.rooms.length}개</h2>
            <ul className="group">
              {g.rooms.map((room) => (
                <li key={room.id} className="room-row">
                  <button type="button" className="row row-button"
                    onClick={() => setDraft({ id: room.id, name: room.name, capacity: room.capacity, equipment: room.equipment })}>
                    <span className={room.bookable ? 'row-icon' : 'row-icon row-icon-muted'}>
                      {room.bookable ? <DoorOpen size={20} /> : <Wrench size={20} />}
                    </span>
                    <span className="row-text">
                      <span className="row-title">
                        {room.name}
                        {!room.bookable && <span className="badge badge-unavailable">점검 중</span>}
                      </span>
                      <span className="row-meta">
                        {room.capacity}명{room.equipment.length > 0 && ` · ${room.equipment.join(', ')}`}
                      </span>
                    </span>
                    <ChevronRight className="row-chevron" size={20} />
                  </button>
                  <button type="button" role="switch" aria-checked={room.bookable} className="switch"
                    aria-label={`${room.name} 예약 받기`} title={room.bookable ? '예약 받는 중' : '점검 중'}
                    onClick={() => toggle(room)}>
                    <span className="switch-knob" />
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      <p className="helper">
        방을 만든 뒤 <Link to={`/orgs/${orgId}/practice/floors`}>평면도 편집</Link>에서 자리를 정해요. 오른쪽 스위치를 끄면 점검 중이 돼요.
      </p>

      {draft && <RoomSheet draft={draft} onSave={save} onClose={() => setDraft(null)} />}
    </main>
  )
}

/** 방 추가·수정 시트. 휴대폰은 아래에서, 넓은 화면은 오른쪽에서 (.sheet). 장비는 칩으로 하나씩 */
function RoomSheet({ draft, onSave, onClose }: { draft: Draft; onSave: (d: Draft) => void; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [d, setD] = useState(draft)
  const [item, setItem] = useState('')

  useEffect(() => {
    dialog.current?.showModal()
  }, [])

  function addItem() {
    const value = item.trim()
    if (value && !d.equipment.includes(value)) setD({ ...d, equipment: [...d.equipment, value] })
    setItem('')
  }

  function onItemKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      addItem()
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const pending = item.trim()
    onSave(pending && !d.equipment.includes(pending) ? { ...d, equipment: [...d.equipment, pending] } : d)
  }

  return (
    <dialog ref={dialog} className="sheet" onClose={onClose} onClick={(e) => e.target === dialog.current && dialog.current?.close()}>
      <form className="sheet-form" onSubmit={submit}>
        <header className="sheet-header">
          <p className="card-title">{d.id ? `${draft.name} 고치기` : '방 추가'}</p>
          <button type="button" className="shell-link" aria-label="닫기" onClick={() => dialog.current?.close()}><X size={20} /></button>
        </header>
        <div className="sheet-body form">
          <Field id="room-name" label="이름">
            <input id="room-name" value={d.name} placeholder="예: A101" required onChange={(e) => setD({ ...d, name: e.target.value })} />
          </Field>
          <Field id="room-capacity" label="수용 인원">
            <input id="room-capacity" type="number" min={1} value={d.capacity} required
              onChange={(e) => setD({ ...d, capacity: Number(e.target.value) })} />
          </Field>
          <Field id="room-item" label="장비">
            <input id="room-item" value={item} placeholder="입력하고 Enter (예: 그랜드 피아노)"
              onChange={(e) => setItem(e.target.value)} onKeyDown={onItemKey} onBlur={addItem} />
          </Field>
          {d.equipment.length > 0 && (
            <div className="chips">
              {d.equipment.map((e) => (
                <span key={e} className="chip chip-removable">
                  {e}
                  <button type="button" aria-label={`${e} 빼기`} onClick={() => setD({ ...d, equipment: d.equipment.filter((x) => x !== e) })}>
                    <X size={14} />
                  </button>
                </span>
              ))}
            </div>
          )}
        </div>
        <footer className="sheet-footer">
          <button className="button button-primary">{d.id ? '저장' : '추가'}</button>
        </footer>
      </form>
    </dialog>
  )
}
