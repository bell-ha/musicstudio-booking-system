package com.musicstudio.practice.domain;

import java.util.List;

/**
 * 층의 격자와 벽·복도 칸. floor.layout jsonb에 통째로 저장한다 (ADR 0009).
 * 칸은 [x, y]이고 0부터 센다. x는 열, y는 행이다.
 */
public record FloorLayout(int width, int height, List<List<Integer>> walls, List<List<Integer>> corridors) {

    public static FloorLayout empty(int width, int height) {
        return new FloorLayout(width, height, List.of(), List.of());
    }

    public List<List<Integer>> walls() {
        return walls == null ? List.of() : walls;
    }

    public List<List<Integer>> corridors() {
        return corridors == null ? List.of() : corridors;
    }
}
