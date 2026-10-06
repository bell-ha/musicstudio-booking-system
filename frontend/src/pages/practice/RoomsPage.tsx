import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, errorMessage } from '../../api'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import type { FloorsResponse, Room } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

const splitEquipment = (text: string) => text.split(',').map((s) => s.trim()).filter(Boolean)

/** UC-21. 방 목록, 추가, 수정, 점검 중 표시 */
export function RoomsPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [data, setData] = useState<FloorsResponse | null>(null)
  const [editing, setEditing] = useState<number | null>(null)
  const [message, setMessage] = useState('')

  const load = useCallback(() => api<FloorsResponse>(`/organizations/${orgId}/practice/floors`).then(setData), [orgId])

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    load().catch((error) => setMessage(errorMessage(error)))
  }, [me, load])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  const floorName = new Map(data?.floors.map((f) => [f.id, f.name]))
  const rooms: Room[] = [...(data?.floors.flatMap((f) => f.rooms) ?? []), ...(data?.unplacedRooms ?? [])]
    .sort((a, b) => a.name.localeCompare(b.name, 'ko'))

  async function send(request: Promise<unknown>) {
    setMessage('')
    try {
      await request
      setEditing(null)
      await load()
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    send(api(`/organizations/${orgId}/practice/rooms`, {
      method: 'POST',
      body: {
        name: form.get('name'),
        capacity: Number(form.get('capacity')),
        equipment: splitEquipment(String(form.get('equipment'))),
      },
    }).then(() => formElement.reset()))
  }

  function update(room: Room, event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    send(api(`/organizations/${orgId}/practice/rooms/${room.id}`, {
      method: 'PATCH',
      body: {
        name: form.get('name'),
        capacity: Number(form.get('capacity')),
        equipment: splitEquipment(String(form.get('equipment'))),
      },
    }))
  }

  const toggleBookable = (room: Room) => {
    const text = room.bookable
      ? `${room.name}을(를) 점검 중으로 바꿀까요? 새 예약을 받지 않아요. 이미 있는 예약은 그대로 남아요.`
      : `${room.name}의 점검을 끝내고 예약을 다시 받을까요?`
    if (window.confirm(text)) {
      send(api(`/organizations/${orgId}/practice/rooms/${room.id}`, { method: 'PATCH', body: { bookable: !room.bookable } }))
    }
  }

  return (
    <main className="page">
      <h1>방 관리</h1>
      {message && <p className="alert" role="alert">{message}</p>}
      {data && rooms.length === 0 && <p className="card empty">아직 방이 없어요. 아래에서 추가해 주세요.</p>}

      <ul className="list">
        {rooms.map((room) => (
          <li className="card" key={room.id}>
            {editing === room.id ? (
              <form className="form" onSubmit={(e) => update(room, e)}>
                <Field id={`name-${room.id}`} label="이름">
                  <input id={`name-${room.id}`} name="name" defaultValue={room.name} required />
                </Field>
                <Field id={`capacity-${room.id}`} label="수용 인원">
                  <input id={`capacity-${room.id}`} name="capacity" type="number" min={1} defaultValue={room.capacity} required />
                </Field>
                <Field id={`equipment-${room.id}`} label="장비 (쉼표로 구분)">
                  <input id={`equipment-${room.id}`} name="equipment" defaultValue={room.equipment.join(', ')} />
                </Field>
                <div className="actions">
                  <button className="button">저장</button>
                  <button className="button" type="button" onClick={() => setEditing(null)}>취소</button>
                </div>
              </form>
            ) : (
              <>
                <p className="card-title">
                  {room.name}
                  {!room.bookable && <span className="badge badge-unavailable">점검 중</span>}
                </p>
                <p className="card-meta">
                  {room.floorId ? floorName.get(room.floorId) : '평면도에 없음'} · {room.capacity}명
                  {room.equipment.length > 0 && ` · ${room.equipment.join(', ')}`}
                </p>
                <div className="actions">
                  <button className="button" onClick={() => setEditing(room.id)}>수정</button>
                  <button className={room.bookable ? 'button button-danger' : 'button'} onClick={() => toggleBookable(room)}>
                    {room.bookable ? '점검 중으로' : '점검 끝'}
                  </button>
                </div>
              </>
            )}
          </li>
        ))}
      </ul>

      <form className="form card" onSubmit={add}>
        <p className="card-title">방 추가</p>
        <Field id="name" label="이름">
          <input id="name" name="name" placeholder="예: A101" required />
        </Field>
        <Field id="capacity" label="수용 인원">
          <input id="capacity" name="capacity" type="number" min={1} defaultValue={1} required />
        </Field>
        <Field id="equipment" label="장비 (쉼표로 구분)">
          <input id="equipment" name="equipment" placeholder="예: 그랜드 피아노, 보면대" />
        </Field>
        <button className="button button-primary">방 추가</button>
      </form>
      <p className="helper">
        방을 만든 뒤 <Link to={`/orgs/${orgId}/practice/floors`}>평면도 편집</Link>에서 자리를 정해요.
      </p>
      <p className="helper"><Link to={`/orgs/${orgId}`}>기관으로 돌아가기</Link></p>
    </main>
  )
}
