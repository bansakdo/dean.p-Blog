package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 방문 이벤트 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface VisitorEventRepository extends JpaRepository<VisitorEvent, UUID> {
}
