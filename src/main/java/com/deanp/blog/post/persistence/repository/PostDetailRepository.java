package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.query.PublicPostQueryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * 게시글 상세 엔티티의 기본 영속성 작업과 공개 글 조회를 제공한다.
 */
public interface PostDetailRepository extends JpaRepository<PostDetail, UUID>, PublicPostQueryRepository {

    /**
     * 시리즈에 속한 게시글을 조회한다.
     *
     * @param seriesId 시리즈 식별자
     * @return 시리즈에 연결된 게시글
     */
    List<PostDetail> findBySeriesId(UUID seriesId);

    /**
     * 같은 시리즈에서 특정 순서를 사용하는 다른 게시글이 있는지 확인한다.
     *
     * @param seriesId 시리즈 식별자
     * @param seriesOrder 시리즈 순서
     * @param id 제외할 게시글 식별자
     * @return 중복 순서가 있으면 true
     */
    boolean existsBySeriesIdAndSeriesOrderAndIdNot(UUID seriesId, Integer seriesOrder, UUID id);
}
