package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

/**
 * 태그 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface TagRepository extends JpaRepository<Tag, UUID> {

    /**
     * @param name 태그 이름
     * @return 대소문자를 구분하지 않고 이름이 같은 태그
     */
    Optional<Tag> findFirstByNameIgnoreCase(String name);

    /**
     * @param slug URL 식별자
     * @return 같은 slug의 태그가 있으면 true
     */
    boolean existsBySlug(String slug);
}
