package com.musicstudio.practice.domain;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private String name;

    private short capacity;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] equipment;

    /** false면 점검 중이라 예약할 수 없다. */
    private boolean bookable;

    /** 평면도 위치. 평면도에서 빠진 방은 다섯 값이 모두 null이다. */
    private Long floorId;
    private Short x;
    private Short y;
    private Short w;
    private Short h;

    protected Room() {
    }

    public Room(Long organizationId, String name, int capacity, List<String> equipment) {
        this.organizationId = organizationId;
        this.name = name;
        this.capacity = (short) capacity;
        this.equipment = equipment.toArray(String[]::new);
        this.bookable = true;
    }

    public void update(String name, Integer capacity, List<String> equipment, Boolean bookable) {
        if (name != null) {
            this.name = name;
        }
        if (capacity != null) {
            this.capacity = capacity.shortValue();
        }
        if (equipment != null) {
            this.equipment = equipment.toArray(String[]::new);
        }
        if (bookable != null) {
            this.bookable = bookable;
        }
    }

    public void place(Long floorId, int x, int y, int w, int h) {
        this.floorId = floorId;
        this.x = (short) x;
        this.y = (short) y;
        this.w = (short) w;
        this.h = (short) h;
    }

    public void unplace() {
        this.floorId = null;
        this.x = this.y = this.w = this.h = null;
    }

    /** 평면도에 있고 점검 중이 아니어야 예약할 수 있다. */
    public boolean canBeBooked() {
        return bookable && floorId != null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<String> getEquipment() {
        return List.of(equipment);
    }

    public boolean isBookable() {
        return bookable;
    }

    public Long getFloorId() {
        return floorId;
    }

    public Short getX() {
        return x;
    }

    public Short getY() {
        return y;
    }

    public Short getW() {
        return w;
    }

    public Short getH() {
        return h;
    }
}
