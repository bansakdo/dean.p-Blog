package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.GroupMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.auth.persistence.entity.GroupMenuId;

/**
 * 권한 그룹별 메뉴 권한 연결의 기본 영속성 작업을 제공한다.
 */
public interface GroupMenuRepository extends JpaRepository<GroupMenu, GroupMenuId> {
}
