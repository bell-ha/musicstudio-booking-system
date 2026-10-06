import type { Cell, Placement } from './types'

/**
 * 지도용: 그린 것(벽·복도·방)이 있는 범위만 남기고 둘레 한 칸을 둔다.
 * 편집기는 60×40 같은 넉넉한 격자를 쓰지만, 지도에서 빈 칸까지 보여 주면 방이 작아지고 화면이 비어 보인다.
 */
export function cropToContent(width: number, height: number, walls: Cell[], corridors: Cell[], rooms: Placement[]) {
  const xs = [...walls, ...corridors].map(([x]) => x).concat(rooms.flatMap((r) => [r.x, r.x + r.w - 1]))
  const ys = [...walls, ...corridors].map(([, y]) => y).concat(rooms.flatMap((r) => [r.y, r.y + r.h - 1]))
  if (xs.length === 0) return { width, height, walls, corridors, rooms }
  const x0 = Math.max(Math.min(...xs) - 1, 0)
  const y0 = Math.max(Math.min(...ys) - 1, 0)
  const x1 = Math.min(Math.max(...xs) + 1, width - 1)
  const y1 = Math.min(Math.max(...ys) + 1, height - 1)
  const shift = ([x, y]: Cell): Cell => [x - x0, y - y0]
  return {
    width: x1 - x0 + 1,
    height: y1 - y0 + 1,
    walls: walls.map(shift),
    corridors: corridors.map(shift),
    rooms: rooms.map((r) => ({ ...r, x: r.x - x0, y: r.y - y0 })),
  }
}
