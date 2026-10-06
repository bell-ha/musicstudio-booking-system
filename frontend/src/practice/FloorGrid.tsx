import type { CSSProperties, PointerEvent, ReactNode } from 'react'
import type { Cell, Placement } from './types'

type Props = {
  width: number
  height: number
  walls: Cell[]
  corridors: Cell[]
  rooms: Placement[]
  /** 방 타일 안에 그릴 것과 클래스. 지도는 상태를, 편집기는 이름만 그린다 */
  renderRoom: (room: Placement) => { className?: string; content: ReactNode; onClick?: () => void }
  /** 편집기: 누른 칸의 좌표. 칸마다 요소를 두지 않고 누른 위치로 칸을 계산한다 (100×100이면 1만 개라서) */
  onCellPointer?: (x: number, y: number) => void
  highlight?: Cell[]
}

/** 평면도. CSS Grid 한 장에 벽·복도 칸과 방 타일을 grid-column/row로 놓는다 (DESIGN 4절) */
export function FloorGrid({ width, height, walls, corridors, rooms, renderRoom, onCellPointer, highlight = [] }: Props) {
  function pointer(event: PointerEvent<HTMLDivElement>) {
    if (!onCellPointer) return
    const rect = event.currentTarget.getBoundingClientRect()
    const x = Math.floor(((event.clientX - rect.left) / rect.width) * width)
    const y = Math.floor(((event.clientY - rect.top) / rect.height) * height)
    if (x >= 0 && y >= 0 && x < width && y < height) onCellPointer(x, y)
  }

  const at = ([x, y]: Cell) => ({ gridColumn: x + 1, gridRow: y + 1 })

  return (
    <div className="floor-scroll">
      <div
        className={onCellPointer ? 'floor floor-editing' : 'floor'}
        style={{ '--cols': width, '--rows': height } as CSSProperties}
        onPointerDown={pointer}
      >
        {walls.map((c) => <div key={`w${c}`} className="floor-wall" style={at(c)} />)}
        {corridors.map((c) => <div key={`c${c}`} className="floor-corridor" style={at(c)} />)}
        {highlight.map((c) => <div key={`h${c}`} className="floor-highlight" style={at(c)} />)}
        {rooms.map((room) => {
          const { className = '', content, onClick } = renderRoom(room)
          const style = { gridColumn: `${room.x + 1} / span ${room.w}`, gridRow: `${room.y + 1} / span ${room.h}` }
          return onClick
            ? <button key={room.id} type="button" className={`floor-room ${className}`} style={style} onClick={onClick}>{content}</button>
            : <div key={room.id} className={`floor-room ${className}`} style={style}>{content}</div>
        })}
      </div>
    </div>
  )
}
