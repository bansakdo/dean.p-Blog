package com.deanp.blog.publishing.persistence.repository;

import com.deanp.blog.publishing.persistence.entity.PublishHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 발행 이력 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface PublishHistoryRepository extends JpaRepository<PublishHistory, UUID> {
}
