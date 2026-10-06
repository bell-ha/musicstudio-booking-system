import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, errorMessage } from '../../api'
import { Field } from '../../Field'
import { isManager } from '../../labels'
import { FloorGrid, type PointerPhase } from '../../practice/FloorGrid'
import type { Cell, Floor, FloorsResponse, Placement, Room } from '../../practice/types'
import { useMyOrganization } from '../../useMyOrganization'
import { NotMember } from '../OrganizationHomePage'

/**
 * 평면도 편집기. v1처럼 펜으로 벽·복도를 끌어서 그리고, 그 안에 방을 끌어서 사각형으로 그린다.
 * 방은 사각형 칸 전체가 지도에서 누를 수 있는 영역이 된다 (ADR 0009).
 */
type Tool = 'wall' | 'corridor' | 'room' | 'erase'
const TOOLS: { tool: Tool; label: string; hint: string }[] = [
  { tool: 'wall', label: '벽', hint: '끌어서 벽을 그려요.' },
  { tool: 'corridor', label: '복도', hint: '끌어서 복도를 그려요.' },
  { tool: 'room', label: '방 그리기', hint: '벽 안쪽을 끌어서 방 크기만큼 사각형을 그려요. 이미 있는 방을 누르면 이름을 바꿔요.' },
  { tool: 'erase', label: '지우개', hint: '끌어서 벽·복도를 지우고, 방을 누르면 평면도에서 빼요.' },
]

type Draft = {
  name: string
  sortOrder: number
  width: number
  height: number
  cells: Map<string, 'wall' | 'corridor'>
  placements: Placement[]
}
type Rect = { x: number; y: number; w: number; h: number }

const key = (x: number, y: number) => `${x},${y}`
const covers = (p: Rect, x: number, y: number) => x >= p.x && x < p.x + p.w && y >= p.y && y < p.y + p.h
const rectOf = (a: Cell, b: Cell): Rect => ({
  x: Math.min(a[0], b[0]), y: Math.min(a[1], b[1]), w: Math.abs(a[0] - b[0]) + 1, h: Math.abs(a[1] - b[1]) + 1,
})
const overlaps = (a: Rect, b: Rect) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

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

function blocked(draft: Draft, rect: Rect) {
  return draft.placements.some((p) => overlaps(p, rect))
    || [...draft.cells.keys()].some((k) => {
      const [x, y] = k.split(',').map(Number)
      return covers(rect, x, y)
    })
}

export function FloorEditorPage() {
  const { orgId } = useParams()
  const me = useMyOrganization(orgId)
  const [data, setData] = useState<FloorsResponse | null>(null)
  const [floorId, setFloorId] = useState<number | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [tool, setTool] = useState<Tool>('wall')
  const [dragStart, setDragStart] = useState<Cell | null>(null)
  const [dragNow, setDragNow] = useState<Cell | null>(null)
  const [pending, setPending] = useState<Rect | null>(null) // 그린 방 사각형. 이름을 정하면 방이 된다
  const [newName, setNewName] = useState('')
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')
  const [stale, setStale] = useState(false)
  const [dirty, setDirty] = useState(false)
  const nameInput = useRef<HTMLInputElement>(null)
  const drag = useRef<{ start: Cell; now: Cell } | null>(null)
  const [createdThisEdit, setCreatedThisEdit] = useState(false) // 이번 편집에서 새로 만든 방이 있나

  const load = useCallback((selectId?: number) => api<FloorsResponse>(`/organizations/${orgId}/practice/floors`).then((loaded) => {
    setData(loaded)
    const floor = loaded.floors.find((f) => f.id === selectId) ?? loaded.floors[0]
    setFloorId(floor?.id ?? null)
    setDraft(floor ? toDraft(floor) : null)
    setStale(false)
    setPending(null)
    setDirty(false)
  }), [orgId])

  useEffect(() => {
    if (!me || !isManager(me.role)) return
    load().catch((error) => setMessage(errorMessage(error)))
  }, [me, load])

  useEffect(() => {
    if (pending) nameInput.current?.focus()
  }, [pending])

  if (me === undefined) return <main className="page" />
  if (me === null || !isManager(me.role)) return <NotMember />

  const floor = data?.floors.find((f) => f.id === floorId) ?? null
  // 배치 안 된 방: 서버가 준 목록 + 이번 편집에서 뺀 방 − 이번 편집에서 놓은 방
  const placedIds = new Set(draft?.placements.map((p) => p.id))
  const removedHere = (floor?.rooms ?? []).filter((r) => !placedIds.has(r.id))
  const unplaced = [...(data?.unplacedRooms ?? []), ...removedHere].filter((r) => !placedIds.has(r.id))

  const edit = (change: (d: Draft) => Draft) => {
    setDraft((d) => (d ? change(d) : d))
    setDirty(true)
  }

  function selectFloor(id: number) {
    if (dirty && !window.confirm('저장하지 않은 변경이 있어요. 다른 층으로 갈까요?')) return
    const next = data!.floors.find((f) => f.id === id)!
    setFloorId(id)
    setDraft(toDraft(next))
    setMessage('')
    setNotice('')
    setPending(null)
    setDirty(false)
  }

  function paint(x: number, y: number) {
    edit((d) => {
      if (d.placements.some((p) => covers(p, x, y))) return d // 방 위에는 칠하지 않는다
      const cells = new Map(d.cells)
      if (tool === 'erase') cells.delete(key(x, y))
      else cells.set(key(x, y), tool as 'wall' | 'corridor')
      return { ...d, cells }
    })
  }

  function onPointer(x: number, y: number, phase: PointerPhase) {
    if (!draft) return
    setMessage('')
    if (tool === 'room') {
      if (phase === 'cancel') { // 끌기가 끊기면 그리던 방은 버린다
        drag.current = null
        setDragStart(null)
        setDragNow(null)
        return
      }
      if (phase === 'down') {
        setPending(null)
        drag.current = { start: [x, y], now: [x, y] }
        setDragStart([x, y])
        setDragNow([x, y])
      } else if (phase === 'move' && drag.current) {
        drag.current.now = [x, y]
        setDragNow([x, y])
      } else if (phase === 'up' && drag.current) {
        const { start, now } = drag.current
        drag.current = null
        setDragStart(null)
        setDragNow(null)
        const tapped = draft.placements.find((p) => covers(p, start[0], start[1]))
        if (tapped && start[0] === now[0] && start[1] === now[1]) {
          rename(tapped) // 끌지 않고 방을 눌렀다 뗐으면 이름 바꾸기 (두 번 탭은 휴대폰에서 믿기 어렵다)
          return
        }
        const rect = rectOf(start, now)
        if (blocked(draft, rect)) {
          setMessage('다른 방이나 벽·복도와 겹쳐요. 벽 안쪽 빈 칸에 그려 주세요.')
          return
        }
        setPending(rect)
        setNewName('')
      }
      return
    }
    if (phase === 'cancel') return // 칠하던 것은 그대로 둔다
    if (tool === 'erase' && phase === 'down') {
      const room = draft.placements.find((p) => covers(p, x, y))
      if (room) {
        edit((d) => ({ ...d, placements: d.placements.filter((p) => p.id !== room.id) }))
        return
      }
    }
    if (phase !== 'up') paint(x, y)
  }

  /** 그린 사각형에 방을 놓는다. 이름을 새로 쓰면 방을 만들고, 기존 방을 고르면 그 방을 옮긴다 */
  async function placeRoom(existing: Room | null) {
    if (!pending || !draft) return
    let room = existing
    if (!room) {
      const name = newName.trim()
      if (!name) return
      try {
        room = await api<Room>(`/organizations/${orgId}/practice/rooms`, { method: 'POST', body: { name } })
        setCreatedThisEdit(true)
        const created = room
        setData((d) => (d ? { ...d, unplacedRooms: [...d.unplacedRooms, created] } : d))
      } catch (error) {
        setMessage(errorMessage(error))
        return
      }
    }
    const placed = room
    const rect = pending
    edit((d) => ({ ...d, placements: [...d.placements, { id: placed.id, name: placed.name, ...rect }] }))
    setPending(null)
    setNewName('')
  }

  /** 방 그리기 도구로 방을 누르면 이름을 바꾼다 (v1의 이름표 고치기) */
  async function rename(room: Placement) {
    const name = window.prompt('방 이름', room.name)?.trim()
    if (!name || name === room.name) return
    try {
      await api(`/organizations/${orgId}/practice/rooms/${room.id}`, { method: 'PATCH', body: { name } })
      setDraft((d) => (d ? { ...d, placements: d.placements.map((p) => (p.id === room.id ? { ...p, name } : p)) } : d))
    } catch (error) {
      setMessage(errorMessage(error))
    }
  }

  /** 다른 층의 벽·복도를 그대로 가져온다 (v1의 복사·붙여넣기). 방은 가져오지 않는다 */
  function copyFrom(sourceId: number) {
    const source = data?.floors.find((f) => f.id === sourceId)
    if (!source || !draft) return
    edit((d) => {
      const cells = new Map(d.cells)
      source.layout.walls.forEach(([x, y]) => { if (x < d.width && y < d.height) cells.set(key(x, y), 'wall') })
      source.layout.corridors.forEach(([x, y]) => { if (x < d.width && y < d.height) cells.set(key(x, y), 'corridor') })
      d.placements.forEach((p) => {
        for (let x = p.x; x < p.x + p.w; x++) for (let y = p.y; y < p.y + p.h; y++) cells.delete(key(x, y))
      })
      return { ...d, cells }
    })
    setNotice(`${source.name}의 벽·복도를 가져왔어요. 저장해야 반영돼요.`)
  }

  async function save() {
    if (!draft || !floor) return
    setMessage('')
    setNotice('')
    const pick = (kind: 'wall' | 'corridor') => [...draft.cells]
      .filter(([k, v]) => {
        const [x, y] = k.split(',').map(Number)
        return v === kind && x < draft.width && y < draft.height // 격자를 줄였으면 밖의 칸은 버린다
      })
      .map(([k]) => k.split(',').map(Number) as Cell)
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
      setCreatedThisEdit(false)
      setNotice('저장했어요.')
    } catch (error) {
      if (error instanceof ApiError && error.problem.status === 412) {
        setStale(true)
        return
      }
      const keptRooms = createdThisEdit ? ' 새로 만든 방은 방 목록에 남아 있어요.' : ''
      const bookings = (error instanceof ApiError && (error.problem as { bookings?: { roomName: string; startsAt: string }[] }).bookings) || []
      setMessage(error instanceof ApiError && error.problem.code === 'ROOM_HAS_FUTURE_BOOKINGS'
        ? `앞으로의 예약이 있는 방은 뺄 수 없어요. 예약을 먼저 취소해 주세요. (${bookings
          .map((b) => `${b.roomName} ${new Date(b.startsAt).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })}`)
          .join(', ')})${keptRooms}`
        : errorMessage(error) + keptRooms)
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

  const dragRect = tool === 'room' && dragStart ? rectOf(dragStart, dragNow ?? dragStart) : null
  const preview = dragRect ? { ...dragRect, invalid: draft ? blocked(draft, dragRect) : false } : pending

  return (
    <main className="page page-wide">
      <h1>평면도 편집</h1>
      <p className="card-meta">펜으로 벽과 복도를 그리고, 그 안에 방을 사각형으로 그려요.</p>
      <p className="notice wide-only-hint">칸이 작아서 그리기 어려워요. 평면도는 넓은 화면(태블릿, PC)에서 편집하는 걸 권해요.</p>

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
            <input aria-label="층 이름" value={draft.name} onChange={(e) => edit((d) => ({ ...d, name: e.target.value }))} />
            <input aria-label="가로 칸 수" type="number" min={1} max={100} className="input-small" value={draft.width}
              onChange={(e) => edit((d) => ({ ...d, width: Number(e.target.value) }))} />
            ×
            <input aria-label="세로 칸 수" type="number" min={1} max={100} className="input-small" value={draft.height}
              onChange={(e) => edit((d) => ({ ...d, height: Number(e.target.value) }))} />
          </div>

          <div className="tabs" role="radiogroup" aria-label="도구">
            {TOOLS.map((t) => (
              <button key={t.tool} role="radio" aria-checked={t.tool === tool} className="tab"
                onClick={() => { setTool(t.tool); setPending(null) }}>{t.label}</button>
            ))}
            {data && data.floors.length > 1 && (
              <select className="select" aria-label="다른 층 구조 가져오기" value=""
                onChange={(e) => { if (e.target.value) copyFrom(Number(e.target.value)) }}>
                <option value="">다른 층 구조 가져오기</option>
                {data.floors.filter((f) => f.id !== floor.id).map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
              </select>
            )}
          </div>
          {/* 안내 줄은 항상 둔다. 줄이 생겼다 사라지면 그리는 사이에 격자가 위아래로 움직인다 */}
          <p className="card-meta hint">{TOOLS.find((t) => t.tool === tool)!.hint}</p>

          <FloorGrid
            width={draft.width}
            height={draft.height}
            walls={[...draft.cells].filter(([, v]) => v === 'wall').map(([k]) => k.split(',').map(Number) as Cell)}
            corridors={[...draft.cells].filter(([, v]) => v === 'corridor').map(([k]) => k.split(',').map(Number) as Cell)}
            rooms={draft.placements}
            renderRoom={(room) => ({ className: 'room-edit', content: room.name })}
            onCellPointer={onPointer}
            preview={preview}
          />

          {pending && (
            <div className="card section">
              <p className="card-title">이 자리에 놓을 방 ({pending.w}×{pending.h}칸)</p>
              <form className="form-row" onSubmit={(e) => { e.preventDefault(); placeRoom(null) }}>
                <input ref={nameInput} aria-label="새 방 이름" placeholder="새 방 이름 (예: A101)" value={newName}
                  onChange={(e) => setNewName(e.target.value)} />
                <button className="button button-primary" disabled={!newName.trim()}>만들기</button>
              </form>
              {unplaced.length > 0 && (
                <div className="actions">
                  <span className="card-meta">또는 이미 있는 방:</span>
                  {unplaced.map((r) => <button key={r.id} className="button" onClick={() => placeRoom(r)}>{r.name}</button>)}
                </div>
              )}
              <button className="button" onClick={() => setPending(null)}>취소</button>
            </div>
          )}

          {stale && (
            <div className="card section">
              <p className="alert" role="alert">다른 관리자가 이 층을 먼저 저장했어요. 바꾼 내용을 버리고 다시 불러와야 해요.
                {createdThisEdit && ' 새로 만든 방은 방 목록에 남아 있어요.'}</p>
              <button className="button button-block" onClick={() => load(floor.id)}>다시 불러오기</button>
            </div>
          )}
          {message && <p className="alert section" role="alert">{message}</p>}
          {notice && <p className="notice section" role="status">{notice}</p>}
          <button className="button button-primary button-block" onClick={save} disabled={stale}>
            {dirty ? '저장' : '저장됨'}
          </button>
        </>
      )}

      <form className="form card section" onSubmit={addFloor}>
        <p className="card-title">층 추가</p>
        <Field id="floorName" label="층 이름">
          <input id="floorName" name="floorName" placeholder="예: 2층" required />
        </Field>
        <div className="form-row">
          <Field id="width" label="가로 칸">
            <input id="width" name="width" type="number" min={1} max={100} defaultValue={30} required />
          </Field>
          <Field id="height" label="세로 칸">
            <input id="height" name="height" type="number" min={1} max={100} defaultValue={30} required />
          </Field>
        </div>
        <button className="button">층 추가</button>
      </form>

      <p className="helper"><Link to={`/orgs/${orgId}`}>기관으로 돌아가기</Link></p>
    </main>
  )
}
