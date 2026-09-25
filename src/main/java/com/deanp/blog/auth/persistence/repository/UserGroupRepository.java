package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.auth.persistence.entity.UserGroupId;

/**
 * 사용자별 권한 그룹 연결의 기본 영속성 작업을 제공한다.
 */
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
}
