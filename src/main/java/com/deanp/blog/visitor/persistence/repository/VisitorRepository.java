package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 방문자 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface VisitorRepository extends JpaRepository<Visitor, UUID> {

    /**
     * 익명 방문자 키와 일치하는 방문자를 조회한다.
     *
     * @param anonymousKey 쿠키에서 식별한 익명 방문자 UUID 문자열
     * @return 일치하는 방문자가 있으면 해당 엔티티
     */
    Optional<Visitor> findByAnonymousKey(String anonymousKey);
}
