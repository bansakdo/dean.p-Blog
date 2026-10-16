package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostSeries;
import java.util.List;
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

    /**
     * @param categoryId 카테고리 식별자
     * @return 이 카테고리를 대표 카테고리로 쓰는 시리즈 수
     */
    long countByCategoryId(UUID categoryId);

    /**
     * @return 모든 시리즈를 정렬 순서와 이름순으로 나열한 목록
     */
    List<PostSeries> findAllByOrderBySortOrderAscNameAsc();
}
