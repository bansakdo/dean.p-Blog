package com.deanp.blog.audit.persistence.repository;

import com.deanp.blog.audit.persistence.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
