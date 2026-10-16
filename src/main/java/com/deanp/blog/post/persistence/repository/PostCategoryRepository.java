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
}
