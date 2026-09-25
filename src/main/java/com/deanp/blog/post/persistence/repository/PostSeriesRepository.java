package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostSeries;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게시글 시리즈의 기본 영속성 작업과 중복 검사를 제공한다.
 */
public interface PostSeriesRepository extends JpaRepository<PostSeries, UUID> {

    /**
     * 시리즈 slug 중복 여부를 확인한다.
     *
     * @param slug URL 식별자
     * @return 이미 존재하면 true
     */
    boolean existsBySlug(String slug);

    /**
     * 자기 자신을 제외하고 시리즈 slug 중복 여부를 확인한다.
     *
     * @param slug URL 식별자
     * @param id 제외할 시리즈 식별자
     * @return 다른 시리즈가 같은 slug를 쓰면 true
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);
}
