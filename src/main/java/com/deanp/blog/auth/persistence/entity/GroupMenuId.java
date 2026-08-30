package com.deanp.blog.auth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMenuId {
    @Column(name = "group_id")
    private UUID groupId;
    @Column(name = "menu_id")
    private UUID menuId;
    private String permissionCode;
}
