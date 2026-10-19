package com.deanp.blog.auth.persistence.query;

import com.deanp.blog.auth.persistence.entity.QAuthGroup;
import com.deanp.blog.auth.persistence.entity.QGroupMenu;
import com.deanp.blog.auth.persistence.entity.QUserGroup;
import com.deanp.blog.menu.persistence.entity.QMenu;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * Querydsl로 사용자·그룹·메뉴 권한 연결을 조회한다.
 */
public class GroupPermissionQueryRepositoryImpl implements GroupPermissionQueryRepository {

    private static final String ACTIVE = "ACTIVE";

    private final JPAQueryFactory queryFactory;

    /**
     * EntityManager로 Querydsl 쿼리 팩토리를 구성한다.
     *
     * @param entityManager JPA 쿼리를 실행할 영속성 컨텍스트
     */
    public GroupPermissionQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    /**
     * 사용자 소속 그룹과 그룹의 메뉴 권한을 조인해 활성 상태인 연결만 인정한다.
     *
     * @param userId 사용자 식별자
     * @param menuPath 메뉴 경로
     * @param permissionCode 권한 코드
     * @return 권한이 있으면 true
     */
    @Override
    public boolean hasActivePermission(UUID userId, String menuPath, String permissionCode) {
        QUserGroup userGroup = QUserGroup.userGroup;
        QAuthGroup group = QAuthGroup.authGroup;
        QGroupMenu groupMenu = QGroupMenu.groupMenu;
        QMenu menu = QMenu.menu;

        return queryFactory.selectOne()
                .from(userGroup)
                .join(group).on(group.id.eq(userGroup.id.groupId))
                .join(groupMenu).on(groupMenu.id.groupId.eq(group.id))
                .join(menu).on(menu.id.eq(groupMenu.id.menuId))
                .where(
                        userGroup.id.userId.eq(userId),
                        group.status.eq(ACTIVE),
                        menu.status.eq(ACTIVE),
                        menu.path.eq(menuPath),
                        groupMenu.id.permissionCode.eq(permissionCode)
                )
                .fetchFirst() != null;
    }
}
