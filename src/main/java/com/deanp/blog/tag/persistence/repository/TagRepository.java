package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * @param slug URL 식별자
     * @param id 제외할 태그 식별자
     * @return 다른 태그가 같은 slug를 쓰면 true
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);

    /**
     * @param name 태그 이름
     * @param id 제외할 태그 식별자
     * @return 다른 태그가 대소문자와 관계없이 같은 이름을 쓰면 true
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
