-- 새 층의 기본 격자를 60×40으로 넓혔다 (원장님은 컴퓨터에서 편집한다. v1은 30×30).
-- 그보다 작은 기존 층도 오른쪽·아래로 빈 칸만 늘린다. 벽·복도·방은 왼쪽 위 기준 좌표라 그대로 제자리다.
-- 줄이지는 않는다: 이미 60×40보다 큰 층은 원장님이 고른 크기다.
-- version을 올려서, 마이그레이션 전에 열어 둔 편집 화면이 옛 크기로 덮어쓰지 못하게 한다 (412).
UPDATE floor
SET layout  = layout
                || jsonb_build_object('width',  greatest((layout ->> 'width')::int, 60),
                                      'height', greatest((layout ->> 'height')::int, 40)),
    version = version + 1
WHERE (layout ->> 'width')::int < 60
   OR (layout ->> 'height')::int < 40;
