import { useRef, type CSSProperties, type PointerEvent, type ReactNode } from 'react'
import type { Cell, Placement } from './types'

/** cancel: 시스템 제스처, 탭 전환 등으로 끌기가 끊겼다. up은 오지 않는다 */
export type PointerPhase = 'down' | 'move' | 'up' | 'cancel'

type Props = {
  width: number
  height: number
  walls: Cell[]
  corridors: Cell[]
  rooms: Placement[]
  /** 방 타일 안에 그릴 것과 클래스. 지도는 상태를, 편집기는 이름만 그린다 */
  renderRoom: (room: Placement) => { className?: string; content: ReactNode; onClick?: () => void }
  /**
   * 편집기: 누르고 끄는 동안의 칸 좌표. v1처럼 끌어서 그린다.
   * 칸마다 요소를 두지 않고 포인터 위치로 칸을 계산한다 (100×100이면 1만 개라서).
   * 빠르게 끌어 건너뛴 칸도 하나씩 'move'로 온다.
   */
  onCellPointer?: (x: number, y: number, phase: PointerPhase) => void
  /** 끌어서 그리는 중인 방 사각형 미리보기 */
  preview?: { x: number; y: number; w: number; h: number; invalid?: boolean } | null
}

/**
 * 누를 수 있는 방은 최소 44px (DESIGN 8절). 가장 작은 방의 짧은 변이 44px 이상이 되도록 칸 크기 하한을 정한다.
 * 방 타일에는 양쪽 2px 여백(.floor-room margin)이 있어서 그만큼 더한다.
 * 다만 한 줄짜리 방 하나 때문에 층 전체 칸이 48px로 커지면 v1처럼 촘촘한 평면도가 깨진다.
 * 그래서 칸 하한은 28px까지만 올린다: 한 줄짜리 방도 24px(WCAG 2.2 AA 최소 대상 크기)은 된다.
 */
const TOUCH = 44
const ROOM_MARGIN = 4
const MAX_CELL_MIN = 28

/** 평면도. CSS Grid 한 장에 벽·복도 칸과 방 타일을 grid-column/row로 놓는다 (DESIGN 4절) */
export function FloorGrid({ width, height, walls, corridors, rooms, renderRoom, onCellPointer, preview }: Props) {
  const last = useRef<Cell | null>(null)
  const activePointer = useRef<number | null>(null) // 여러 손가락 중 처음 누른 하나만 따른다

  function cellOf(event: PointerEvent<HTMLDivElement>): Cell | null {
    const rect = event.currentTarget.getBoundingClientRect()
    const x = Math.floor(((event.clientX - rect.left) / rect.width) * width)
    const y = Math.floor(((event.clientY - rect.top) / rect.height) * height)
    return x >= 0 && y >= 0 && x < width && y < height ? [x, y] : null
  }

  function end() {
    last.current = null
    activePointer.current = null
  }

  function handle(event: PointerEvent<HTMLDivElement>, phase: PointerPhase) {
    if (!onCellPointer) return
    if (phase === 'down') {
      if (event.button !== 0 || activePointer.current !== null) return
      activePointer.current = event.pointerId
      event.currentTarget.setPointerCapture(event.pointerId)
    } else if (event.pointerId !== activePointer.current) {
      return
    }
    if (phase === 'cancel') {
      end()
      onCellPointer(-1, -1, 'cancel')
      return
    }
    const cell = cellOf(event)
    if (phase === 'up') {
      end()
      onCellPointer(cell?.[0] ?? -1, cell?.[1] ?? -1, 'up')
      return
    }
    if (!cell) return
    const from = last.current
    if (phase === 'move' && from && from[0] === cell[0] && from[1] === cell[1]) return // 같은 칸 안에서 움직인 것은 무시
    last.current = cell
    if (phase === 'move' && from) {
      // 빠르게 끌면 이벤트 사이에 칸을 건너뛴다. 직전 칸과 지금 칸 사이를 선으로 채워 그린 선이 끊기지 않게 한다
      const steps = Math.max(Math.abs(cell[0] - from[0]), Math.abs(cell[1] - from[1]))
      for (let i = 1; i < steps; i++) {
        onCellPointer(Math.round(from[0] + ((cell[0] - from[0]) * i) / steps),
          Math.round(from[1] + ((cell[1] - from[1]) * i) / steps), 'move')
      }
    }
    onCellPointer(cell[0], cell[1], phase)
  }

  const at = ([x, y]: Cell) => ({ gridColumn: x + 1, gridRow: y + 1 })
  const area = (r: { x: number; y: number; w: number; h: number }) =>
    ({ gridColumn: `${r.x + 1} / span ${r.w}`, gridRow: `${r.y + 1} / span ${r.h}` })
  const smallestSide = Math.min(...rooms.map((r) => Math.min(r.w, r.h)), 4)
  const cellMin = `${Math.min(Math.ceil((TOUCH + ROOM_MARGIN) / smallestSide), MAX_CELL_MIN)}px`

  return (
    <div className="floor-scroll">
      <div
        className={onCellPointer ? 'floor floor-editing' : 'floor'}
        style={{ '--cols': width, '--rows': height, '--cell-min': cellMin } as CSSProperties}
        onPointerDown={(e) => handle(e, 'down')}
        onPointerMove={(e) => handle(e, 'move')}
        onPointerUp={(e) => handle(e, 'up')}
        onPointerCancel={(e) => handle(e, 'cancel')}
        onLostPointerCapture={(e) => { if (e.pointerId === activePointer.current) handle(e, 'cancel') }}
      >
        {walls.map((c) => <div key={`w${c}`} className="floor-wall" style={at(c)} />)}
        {corridors.map((c) => <div key={`c${c}`} className="floor-corridor" style={at(c)} />)}
        {rooms.map((room) => {
          const { className: base = '', content, onClick } = renderRoom(room)
          // 한 줄짜리·좁은 방은 이름만 보이게 한다 (상태 글자는 색으로 보이고, 화면 낭독기에는 그대로 읽힌다)
          const className = room.h === 1 || room.w <= 2 ? `${base} floor-room-compact` : base
          return onClick
            ? <button key={room.id} type="button" className={`floor-room ${className}`} style={area(room)} onClick={onClick}>{content}</button>
            : <div key={room.id} className={`floor-room ${className}`} style={area(room)}>{content}</div>
        })}
        {preview && <div className={preview.invalid ? 'floor-preview floor-preview-invalid' : 'floor-preview'} style={area(preview)} />}
      </div>
    </div>
  )
}
