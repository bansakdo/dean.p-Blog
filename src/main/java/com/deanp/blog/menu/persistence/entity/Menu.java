package com.deanp.blog.menu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 제어와 화면 이동에 사용할 메뉴 정보를 blog.menu 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "menu", schema = "blog")
public class Menu {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name")
    private String name;

    @Column(name = "path")
    private String path;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Menu parent;
}
