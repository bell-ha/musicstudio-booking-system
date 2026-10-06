package com.musicstudio.practice.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private String name;

    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    private FloorLayout layout;

    /** 두 관리자가 같은 층을 동시에 저장하면 나중 것이 거절된다 (UC-20 5a). */
    @Version
    private long version;

    protected Floor() {
    }

    public Floor(Long organizationId, String name, int sortOrder, int width, int height) {
        this.organizationId = organizationId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.layout = FloorLayout.empty(width, height);
    }

    /** 바뀐 것이 있으면 true. 그때는 Hibernate가 저장하면서 버전을 올린다. */
    public boolean update(String name, int sortOrder, FloorLayout layout) {
        boolean changed = !name.equals(this.name) || sortOrder != this.sortOrder || !layout.equals(this.layout);
        this.name = name;
        this.sortOrder = sortOrder;
        this.layout = layout;
        return changed;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public FloorLayout getLayout() {
        return layout;
    }

    public long getVersion() {
        return version;
    }
}
