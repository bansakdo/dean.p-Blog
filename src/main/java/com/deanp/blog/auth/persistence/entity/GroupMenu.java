package com.deanp.blog.auth.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.menu.persistence.entity.Menu;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 권한 그룹과 메뉴 및 권한 코드의 연결을 blog.group_menu 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "group_menu", schema = "blog")
public class GroupMenu {

    @EmbeddedId
    private GroupMenuId id;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private AuthGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", insertable = false, updatable = false)
    private Menu menu;

    /**
     * 그룹에 메뉴 권한을 부여한다.
     *
     * @param id 그룹·메뉴·권한 코드 복합키
     * @return 저장할 권한 연결
     */
    public static GroupMenu create(GroupMenuId id) {
        GroupMenu groupMenu = new GroupMenu();
        groupMenu.id = id;
        groupMenu.createdAt = Instant.now();
        return groupMenu;
    }
}
