package com.deanp.blog.audit.persistence.repository;

import com.deanp.blog.audit.persistence.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 감사 로그 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
