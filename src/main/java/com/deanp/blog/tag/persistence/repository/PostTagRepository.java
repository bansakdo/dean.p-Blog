package com.deanp.blog.tag.persistence.repository;

import com.deanp.blog.tag.persistence.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * @param tagId 태그 식별자
     * @return 태그가 붙은 게시글 연결
     */
    List<PostTag> findByIdTagId(UUID tagId);

    /**
     * @param tagId 태그 식별자
     * @return 태그가 붙은 게시글 수
     */
    long countByIdTagId(UUID tagId);

    /**
     * 삭제한 글을 포함해 태그별 연결 수를 센다.
     *
     * @return 태그 식별자별 연결 수
     */
    @Query("""
            select new com.deanp.blog.tag.persistence.repository.PostTagRepository$TagCount(pt.id.tagId, count(pt))
            from PostTag pt group by pt.id.tagId""")
    List<TagCount> countByTag();

    /**
     * 태그별 연결 수다.
     *
     * @param tagId 태그 식별자
     * @param count 연결된 게시글 수
     */
    record TagCount(UUID tagId, long count) {
    }
}
