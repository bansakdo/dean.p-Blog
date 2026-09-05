package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.query.PublicPostQueryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * 게시글 상세 엔티티의 기본 영속성 작업과 공개 글 조회를 제공한다.
 */
public interface PostDetailRepository extends JpaRepository<PostDetail, UUID>, PublicPostQueryRepository {
}
