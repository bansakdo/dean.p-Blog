package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.deanp.blog.tag.persistence.entity.PostTagId;

/**
 * 게시글별 태그 연결의 기본 영속성 작업을 제공한다.
 */
public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {

    /**
     * @param postDetailId 게시글 식별자
     * @return 게시글에 붙은 태그 연결
     */
    List<PostTag> findByIdPostDetailId(UUID postDetailId);
}
