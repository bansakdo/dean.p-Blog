package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 게시글 묶음 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface PostRepository extends JpaRepository<Post, UUID> {
}
