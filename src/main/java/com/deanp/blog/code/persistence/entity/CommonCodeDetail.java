package com.deanp.blog.code.persistence.entity;

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
 * 공통 코드 그룹에 속한 개별 코드 값을 blog.common_code_detail 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "common_code_detail", schema = "blog")
public class CommonCodeDetail {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "common_code_id")
    private UUID commonCodeId;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_code_id", insertable = false, updatable = false)
    private CommonCode commonCode;
}
