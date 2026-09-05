package com.deanp.blog.publishing.persistence.repository;

import com.deanp.blog.publishing.persistence.entity.PostRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 게시글 리비전 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface PostRevisionRepository extends JpaRepository<PostRevision, UUID> {
}
