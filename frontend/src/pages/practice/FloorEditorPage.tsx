import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, errorMessage } from '../../api'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { FloorGrid } from '../../practice/FloorGrid'
import type { Cell, Floor, FloorsResponse, Placement, Room } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

type Tool = 'wall' | 'corridor' | 'erase' | 'room'
const TOOLS: { tool: Tool; label: string }[] = [
  { tool: 'wall', label: '벽' },
  { tool: 'corridor', label: '복도' },
  { tool: 'room', label: '방 놓기' },
  { tool: 'erase', label: '지우개' },
]

/** 저장 전까지의 편집 상태. 벽·복도는 "x,y" 키로 들고 있다가 저장할 때 배열로 바꾼다 */
type Draft = {
  name: string
  sortOrder: number
  width: number
  height: number
  cells: Map<string, 'wall' | 'corridor'>
  placements: Placement[]
}

const key = (x: number, y: number) => `${x},${y}`
const covers = (p: Placement, x: number, y: number) => x >= p.x && x < p.x + p.w && y >= p.y && y < p.y + p.h

function toDraft(floor: Floor): Draft {
  const cells = new Map<string, 'wall' | 'corridor'>()
  floor.layout.walls.forEach(([x, y]) => cells.set(key(x, y), 'wall'))
  floor.layout.corridors.forEach(([x, y]) => cells.set(key(x, y), 'corridor'))
  return {
    name: floor.name,
    sortOrder: floor.sortOrder,
    width: floor.layout.width,
    height: floor.layout.height,
    cells,
    placements: floor.rooms.map((r) => ({ id: r.id, name: r.name, x: r.x!, y: r.y!, w: r.w!, h: r.h! })),
  }
}

export function FloorEditorPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [data, setData] = useState<FloorsResponse | null>(null)
  const [floorId, setFloorId] = useState<number | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [tool, setTool] = useState<Tool>('wall')
  const [pickedRoom, setPickedRoom] = useState<Room | null>(null)
  const [corner, setCorner] = useState<Cell | null>(null)
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')
  const [stale, setStale] = useState(false)

  // 불러온 뒤 selectId 층(없으면 첫 층)을 편집 상태로 연다
  const load = useCallback((selectId?: number) => api<FloorsResponse>(`/organizations/${orgId}/practice/floors`).then((loaded) => {
    setData(loaded)
    const floor = loaded.floors.find((f) => f.id === selectId) ?? loaded.floors[0]
    setFloorId(floor?.id ?? null)
    setDraft(floor ? toDraft(floor) : null)
    setStale(false)
    setPickedRoom(null)
    setCorner(null)
  }), [orgId])

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    load().catch((error) => setMessage(errorMessage(error)))
  }, [me, load])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  const floor = data?.floors.find((f) => f.id === floorId) ?? null
  // 배치 안 된 방: 서버가 준 목록 + 이번 편집에서 뺀 방 − 이번 편집에서 놓은 방
  const placedIds = new Set(draft?.placements.map((p) => p.id))
  const removedHere = (floor?.rooms ?? []).filter((r) => !placedIds.has(r.id))
  const unplaced = [...(data?.unplacedRooms ?? []), ...removedHere].filter((r) => !placedIds.has(r.id))

  function selectFloor(id: number) {
    const next = data!.floors.find((f) => f.id === id)!
    setFloorId(id)
    setDraft(toDraft(next))
    setMessage('')
    setNotice('')
    setCorner(null)
  }

  function onCell(x: number, y: number) {
    if (!draft) return
    setMessage('')
    const room = draft.placements.find((p) => covers(p, x, y))
    if (tool === 'erase') {
      if (room) setDraft({ ...draft, placements: draft.placements.filter((p) => p !== room) })
      else if (draft.cells.has(key(x, y))) {
        const cells = new Map(draft.cells)
        cells.delete(key(x, y))
        setDraft({ ...draft, cells })
      }
      return
    }
    if (tool === 'wall' || tool === 'corridor') {
      if (room) return // 방 위에는 칠하지 않는다
      const cells = new Map(draft.cells)
      if (cells.get(key(x, y)) === tool) cells.delete(key(x, y))
      else cells.set(key(x, y), tool)
      setDraft({ ...draft, cells })
      return
    }
    // 방 놓기: 방을 고르고 두 모서리를 차례로 누른다
    if (!pickedRoom) {
      setMessage('먼저 아래에서 놓을 방을 골라 주세요.')
      return
    }
    if (!corner) {
      setCorner([x, y])
      return
    }
    const placement: Placement = {
      id: pickedRoom.id,
      name: pickedRoom.name,
      x: Math.min(corner[0], x),
      y: Math.min(corner[1], y),
      w: Math.abs(corner[0] - x) + 1,
      h: Math.abs(corner[1] - y) + 1,
    }
    setCorner(null)
    const blocked = draft.placements.some((p) => p.x < placement.x + placement.w && placement.x < p.x + p.w
      && p.y < placement.y + placement.h && placement.y < p.y + p.h)
      || [...draft.cells.keys()].some((k) => {
        const [cx, cy] = k.split(',').map(Number)
        return covers(placement, cx, cy)
      })
    if (blocked) {
      setMessage('다른 방이나 벽·복도와 겹쳐요. 빈 칸에 놓아 주세요.')
      return
    }
    setDraft({ ...draft, placements: [...draft.placements, placement] })
    setPickedRoom(null)
  }

  async function save() {
    if (!draft || !floor) return
    setMessage('')
    setNotice('')
    const pick = (kind: 'wall' | 'corridor') =>
      [...draft.cells].filter(([, v]) => v === kind).map(([k]) => k.split(',').map(Number) as Cell)
    try {
      const saved = await api<Floor>(`/organizations/${orgId}/practice/floors/${floor.id}`, {
        method: 'PUT',
        headers: { 'If-Match': `"${floor.version}"` },
        body: {
          name: draft.name,
          sortOrder: draft.sortOrder,
          layout: { width: draft.width, height: draft.height, walls: pick('wall'), corridors: pick('corridor') },
          rooms: draft.placements.map(({ id, x, y, w, h }) => ({ id, x, y, w, h })),
        },
      })
      await load(saved.id)
      setNotice('저장했어요.')
    } catch (error) {
      if (error instanceof ApiError && error.problem.status === 412) {
        setStale(true)
        return
      }
      const bookings = (error instanceof ApiError && (error.problem as { bookings?: { roomName: string; startsAt: string }[] }).bookings) || []
      setMessage(error instanceof ApiError && error.problem.code === 'ROOM_HAS_FUTURE_BOOKINGS'
        ? `앞으로의 예약이 있는 방은 뺄 수 없어요. 예약을 먼저 취소해 주세요. (${bookings
          .map((b) => `${b.roomName} ${new Date(b.startsAt).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })}`)
          .join(', ')})`
        : errorMessage(error))
    }
  }

  async function addFloor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      const created = await api<Floor>(`/organizations/${orgId}/practice/floors`, {
        method: 'POST',
        body: {
          name: form.get('floorName'),
          sortOrder: (data?.floors.length ?? 0) + 1,
          width: Number(form.get('width')),
          height: Number(form.get('height')),
        },
      })
      await load(created.id)
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  return (
    <main className="page page-wide">
      <h1>평면도 편집</h1>

      {data && data.floors.length > 0 && (
        <div className="tabs" role="tablist">
          {data.floors.map((f) => (
            <button key={f.id} role="tab" className="tab" aria-selected={f.id === floorId} onClick={() => selectFloor(f.id)}>
              {f.name}
            </button>
          ))}
        </div>
      )}

      {draft && floor && (
        <>
          <div className="form-row">
            <input aria-label="층 이름" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            <input aria-label="가로 칸 수" type="number" min={1} max={100} className="input-small" value={draft.width}
              onChange={(e) => setDraft({ ...draft, width: Number(e.target.value) })} />
            ×
            <input aria-label="세로 칸 수" type="number" min={1} max={100} className="input-small" value={draft.height}
              onChange={(e) => setDraft({ ...draft, height: Number(e.target.value) })} />
          </div>

          <div className="tabs" role="radiogroup" aria-label="도구">
            {TOOLS.map((t) => (
              <button key={t.tool} role="radio" aria-checked={t.tool === tool} className="tab"
                onClick={() => { setTool(t.tool); setCorner(null) }}>{t.label}</button>
            ))}
          </div>

          {tool === 'room' && (
            <div className="actions">
              {unplaced.length === 0 && <p className="card-meta">놓을 방이 없어요. <Link to={`/orgs/${orgId}/practice/rooms`}>방 관리</Link>에서 먼저 만들어 주세요.</p>}
              {unplaced.map((r) => (
                <button key={r.id} className="button" aria-pressed={pickedRoom?.id === r.id}
                  onClick={() => { setPickedRoom(r); setCorner(null) }}>{r.name}</button>
              ))}
              {/* 안내 줄은 항상 둔다. 줄이 생겼다 사라지면 방을 놓는 사이에 격자가 위아래로 움직인다 */}
              <p className="card-meta hint">
                {!pickedRoom ? '놓을 방을 골라 주세요.'
                  : corner ? '반대쪽 모서리 칸을 눌러 주세요.' : `${pickedRoom.name}의 한쪽 모서리 칸을 눌러 주세요.`}
              </p>
            </div>
          )}

          <FloorGrid
            width={draft.width}
            height={draft.height}
            walls={[...draft.cells].filter(([, v]) => v === 'wall').map(([k]) => k.split(',').map(Number) as Cell)}
            corridors={[...draft.cells].filter(([, v]) => v === 'corridor').map(([k]) => k.split(',').map(Number) as Cell)}
            rooms={draft.placements}
            renderRoom={(room) => ({ className: 'room-edit', content: room.name })}
            onCellPointer={onCell}
            highlight={corner ? [corner] : []}
          />

          {stale && (
            <div className="card section">
              <p className="alert" role="alert">다른 관리자가 이 층을 먼저 저장했어요. 바꾼 내용을 버리고 다시 불러와야 해요.</p>
              <button className="button button-block" onClick={() => load(floor.id)}>다시 불러오기</button>
            </div>
          )}
          {message && <p className="alert section" role="alert">{message}</p>}
          {notice && <p className="notice section" role="status">{notice}</p>}
          <button className="button button-primary button-block" onClick={save} disabled={stale}>저장</button>
        </>
      )}

      <form className="form card section" onSubmit={addFloor}>
        <p className="card-title">층 추가</p>
        <Field id="floorName" label="층 이름">
          <input id="floorName" name="floorName" placeholder="예: 2층" required />
        </Field>
        <div className="form-row">
          <Field id="width" label="가로 칸">
            <input id="width" name="width" type="number" min={1} max={100} defaultValue={20} required />
          </Field>
          <Field id="height" label="세로 칸">
            <input id="height" name="height" type="number" min={1} max={100} defaultValue={12} required />
          </Field>
        </div>
        <button className="button">층 추가</button>
      </form>

      <p className="helper"><Link to={`/orgs/${orgId}`}>기관으로 돌아가기</Link></p>
    </main>
  )
}
