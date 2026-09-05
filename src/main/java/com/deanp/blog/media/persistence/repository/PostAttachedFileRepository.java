package com.deanp.blog.media.persistence.repository;

import com.deanp.blog.media.persistence.entity.PostAttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;

/**
 * 게시글별 첨부 파일 연결의 기본 영속성 작업을 제공한다.
 */
public interface PostAttachedFileRepository extends JpaRepository<PostAttachedFile, PostAttachedFileId> {
}
