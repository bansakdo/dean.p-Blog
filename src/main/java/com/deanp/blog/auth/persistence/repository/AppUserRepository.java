package com.deanp.blog.auth.persistence.repository;

import com.deanp.blog.auth.persistence.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 사용자 계정 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
