package com.deanp.blog.auth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * 사용자와 권한 그룹으로 구성된 user_group 복합 기본키를 표현한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGroupId {
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "group_id")
    private UUID groupId;
}
