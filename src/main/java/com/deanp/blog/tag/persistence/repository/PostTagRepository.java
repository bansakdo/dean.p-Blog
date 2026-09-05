package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.tag.persistence.entity.PostTagId;

/**
 * 게시글별 태그 연결의 기본 영속성 작업을 제공한다.
 */
public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {
}
