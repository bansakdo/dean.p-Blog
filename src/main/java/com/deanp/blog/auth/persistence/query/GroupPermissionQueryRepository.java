package com.deanp.blog.auth.persistence.query;

import java.util.UUID;

/**
 * 사용자가 속한 그룹의 메뉴 권한을 조회한다.
 */
public interface GroupPermissionQueryRepository {

    /**
     * 사용자가 활성 그룹을 통해 활성 메뉴의 권한을 가졌는지 확인한다.
     *
     * @param userId 사용자 식별자
     * @param menuPath 메뉴 경로
     * @param permissionCode 권한 코드
     * @return 권한이 있으면 true
     */
    boolean hasActivePermission(UUID userId, String menuPath, String permissionCode);
}
