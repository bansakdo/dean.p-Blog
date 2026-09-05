package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 태그 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface TagRepository extends JpaRepository<Tag, UUID> {
}
