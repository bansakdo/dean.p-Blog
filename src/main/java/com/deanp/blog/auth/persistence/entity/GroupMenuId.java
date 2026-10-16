package com.deanp.blog.auth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * 권한 그룹, 메뉴, 권한 코드로 구성된 group_menu 복합 기본키를 표현한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMenuId {
    @Column(name = "group_id")
    private UUID groupId;
    @Column(name = "menu_id")
    private UUID menuId;
    @Column(name = "permission_code")
    private String permissionCode;

    /**
     * @param groupId 그룹 식별자
     * @param menuId 메뉴 식별자
     * @param permissionCode 권한 코드
     */
    public GroupMenuId(UUID groupId, UUID menuId, String permissionCode) {
        this.groupId = groupId;
        this.menuId = menuId;
        this.permissionCode = permissionCode;
    }
}
