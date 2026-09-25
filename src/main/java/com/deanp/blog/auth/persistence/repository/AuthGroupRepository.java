package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.AuthGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 권한 그룹 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface AuthGroupRepository extends JpaRepository<AuthGroup, UUID> {
}
