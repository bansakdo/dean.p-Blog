package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

/**
 * 게시글 카테고리 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface PostCategoryRepository extends JpaRepository<PostCategory, UUID> {

    /**
     * @param status 카테고리 상태
     * @return 해당 상태의 카테고리를 정렬 순서와 이름순으로 나열한 목록
     */
    List<PostCategory> findByStatusOrderBySortOrderAscNameAsc(String status);

    /**
     * @param slug URL 식별자
     * @return 같은 slug의 카테고리가 있으면 true
     */
    boolean existsBySlug(String slug);

    /**
     * @param slug URL 식별자
     * @param id 제외할 카테고리 식별자
     * @return 다른 카테고리가 같은 slug를 쓰면 true
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);

    /**
     * @param name 표시 이름
     * @return 대소문자와 관계없이 같은 이름의 카테고리가 있으면 true
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * @param name 표시 이름
     * @param id 제외할 카테고리 식별자
     * @return 다른 카테고리가 대소문자와 관계없이 같은 이름을 쓰면 true
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * @param parentId 상위 카테고리 식별자
     * @return 이 카테고리를 상위로 둔 카테고리가 있으면 true
     */
    boolean existsByParentId(UUID parentId);

    /**
     * @return 모든 카테고리를 정렬 순서와 이름순으로 나열한 목록
     */
    List<PostCategory> findAllByOrderBySortOrderAscNameAsc();
}
