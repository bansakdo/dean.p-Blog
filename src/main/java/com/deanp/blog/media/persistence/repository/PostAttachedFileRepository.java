package com.deanp.blog.media.persistence.repository;

import com.deanp.blog.media.persistence.entity.PostAttachedFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;

/**
 * 게시글별 첨부 파일 연결의 기본 영속성 작업을 제공한다.
 */
public interface PostAttachedFileRepository extends JpaRepository<PostAttachedFile, PostAttachedFileId> {

    /**
     * @param postDetailId 게시글 식별자
     * @return 게시글의 첨부 연결을 첨부 순서대로 나열한 목록
     */
    List<PostAttachedFile> findByIdPostDetailIdOrderBySortOrderAsc(UUID postDetailId);

    /**
     * @param postDetailId 게시글 식별자
     * @return 게시글에 첨부된 파일 수
     */
    int countByIdPostDetailId(UUID postDetailId);
}
