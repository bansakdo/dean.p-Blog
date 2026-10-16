package com.deanp.blog.auth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 그룹 정보를 blog.auth_group 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "auth_group", schema = "blog")
public class AuthGroup {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 활성 권한 그룹을 만든다.
     *
     * @param name 그룹 이름
     * @param description 그룹 설명
     * @return 저장할 그룹
     */
    public static AuthGroup create(String name, String description) {
        AuthGroup group = new AuthGroup();
        Instant now = Instant.now();
        group.id = UUID.randomUUID();
        group.name = name;
        group.description = description;
        group.status = "ACTIVE";
        group.createdAt = now;
        group.updatedAt = now;
        return group;
    }
}
