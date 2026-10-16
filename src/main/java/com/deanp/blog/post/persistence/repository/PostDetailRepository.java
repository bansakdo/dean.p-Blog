package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.query.PublicPostQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /**
     * slug 고유 인덱스가 삭제하지 않은 글에만 걸려 있으므로 제외할 상태를 함께 받는다.
     *
     * @param slug URL 식별자
     * @param status 검사에서 뺄 글 상태
     * @return 그 상태가 아닌 글 중 같은 slug의 글이 있으면 true
     */
    boolean existsBySlugAndStatusNot(String slug, String status);

    /**
     * @param slug URL 식별자
     * @param status 검사에서 뺄 글 상태
     * @param id 제외할 게시글 식별자
     * @return 그 상태가 아닌 다른 글이 같은 slug를 쓰고 있으면 true
     */
    boolean existsBySlugAndStatusNotAndIdNot(String slug, String status, UUID id);

    /**
     * 수정·삭제·복구가 서로의 상태 변경을 덮어쓰지 않도록 행을 잠그고 글을 읽는다.
     *
     * @param id 게시글 식별자
     * @return 트랜잭션이 끝날 때까지 잠긴 글, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PostDetail p where p.id = :id")
    Optional<PostDetail> findForUpdateById(UUID id);

    /**
     * 관리 목록에서 상태와 제목 검색어로 글을 고른다.
     *
     * @param statuses 포함할 글 상태
     * @param title 제목에 들어 있어야 하는 검색어, 빈 문자열이면 모든 제목
     * @param pageable 페이지와 정렬
     * @return 조건에 맞는 글 한 페이지
     */
    Page<PostDetail> findByStatusInAndTitleContainingIgnoreCase(Collection<String> statuses, String title, Pageable pageable);
}
