import { useRef, type CSSProperties, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import type { Cell, Placement } from './types'

export type PointerPhase = 'down' | 'move' | 'up'

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
   */
  onCellPointer?: (x: number, y: number, phase: PointerPhase) => void
  /** 편집기: 두 번 누른 칸 (방 이름 바꾸기) */
  onCellDoubleClick?: (x: number, y: number) => void
  /** 끌어서 그리는 중인 방 사각형 미리보기 */
  preview?: { x: number; y: number; w: number; h: number; invalid?: boolean } | null
}

/** 평면도. CSS Grid 한 장에 벽·복도 칸과 방 타일을 grid-column/row로 놓는다 (DESIGN 4절) */
export function FloorGrid({ width, height, walls, corridors, rooms, renderRoom, onCellPointer, onCellDoubleClick, preview }: Props) {
  const last = useRef<string | null>(null)

  function cellOf(event: PointerEvent<HTMLDivElement> | MouseEvent<HTMLDivElement>): Cell | null {
    const rect = event.currentTarget.getBoundingClientRect()
    const x = Math.floor(((event.clientX - rect.left) / rect.width) * width)
    const y = Math.floor(((event.clientY - rect.top) / rect.height) * height)
    return x >= 0 && y >= 0 && x < width && y < height ? [x, y] : null
  }

  function handle(event: PointerEvent<HTMLDivElement>, phase: PointerPhase) {
    if (!onCellPointer) return
    if (phase === 'down') {
      if (event.button !== 0) return
      event.currentTarget.setPointerCapture(event.pointerId)
    } else if (!event.currentTarget.hasPointerCapture(event.pointerId)) {
      return
    }
    const cell = cellOf(event)
    if (phase === 'up') {
      event.currentTarget.releasePointerCapture(event.pointerId)
      last.current = null
      onCellPointer(cell?.[0] ?? -1, cell?.[1] ?? -1, 'up')
      return
    }
    if (!cell) return
    const k = `${cell[0]},${cell[1]}`
    if (phase === 'move' && k === last.current) return // 같은 칸 안에서 움직인 것은 무시
    // 빠르게 끌면 이벤트 사이에 칸을 건너뛴다. 직전 칸과 지금 칸 사이를 선으로 채워 그린 선이 끊기지 않게 한다
    const from = phase === 'move' && last.current ? last.current.split(',').map(Number) : null
    last.current = k
    if (from) {
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

  return (
    <div className="floor-scroll">
      <div
        className={onCellPointer ? 'floor floor-editing' : 'floor'}
        style={{ '--cols': width, '--rows': height } as CSSProperties}
        onPointerDown={(e) => handle(e, 'down')}
        onPointerMove={(e) => handle(e, 'move')}
        onPointerUp={(e) => handle(e, 'up')}
        onDoubleClick={(e) => {
          const cell = onCellDoubleClick && cellOf(e)
          if (cell) onCellDoubleClick(cell[0], cell[1])
        }}
      >
        {walls.map((c) => <div key={`w${c}`} className="floor-wall" style={at(c)} />)}
        {corridors.map((c) => <div key={`c${c}`} className="floor-corridor" style={at(c)} />)}
        {rooms.map((room) => {
          const { className = '', content, onClick } = renderRoom(room)
          return onClick
            ? <button key={room.id} type="button" className={`floor-room ${className}`} style={area(room)} onClick={onClick}>{content}</button>
            : <div key={room.id} className={`floor-room ${className}`} style={area(room)}>{content}</div>
        })}
        {preview && <div className={preview.invalid ? 'floor-preview floor-preview-invalid' : 'floor-preview'} style={area(preview)} />}
      </div>
    </div>
  )
}
