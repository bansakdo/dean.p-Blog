package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 게시글 카테고리 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface PostCategoryRepository extends JpaRepository<PostCategory, UUID> {
}
