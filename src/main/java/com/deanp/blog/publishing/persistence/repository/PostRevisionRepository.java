package com.deanp.blog.publishing.persistence.repository;

import com.deanp.blog.publishing.persistence.entity.PostRevision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 게시글 리비전 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface PostRevisionRepository extends JpaRepository<PostRevision, UUID> {

    /**
     * @param postDetailId 게시글 식별자
     * @return 게시글의 마지막 리비전, 없으면 빈 값
     */
    Optional<PostRevision> findTopByPostDetailIdOrderByRevisionNoDesc(UUID postDetailId);

    /**
     * 본문은 읽지 않고 이력 목록에 필요한 값만 조회한다.
     *
     * @param postDetailId 게시글 식별자
     * @return 게시글의 리비전 요약 목록, 최신 버전부터
     */
    @Query("""
            select new com.deanp.blog.publishing.persistence.repository.PostRevisionRepository$RevisionSummary(
                r.revisionNo, r.changeType, r.createdAt, u.loginId)
            from PostRevision r left join r.changedByUser u
            where r.postDetailId = :postDetailId
            order by r.revisionNo desc""")
    List<RevisionSummary> findSummariesByPostDetailId(UUID postDetailId);

    /**
     * @param postDetailId 게시글 식별자
     * @param revisionNo 버전 번호
     * @return 게시글의 해당 버전, 없으면 빈 값
     */
    @EntityGraph(attributePaths = "changedByUser")
    Optional<PostRevision> findByPostDetailIdAndRevisionNo(UUID postDetailId, int revisionNo);

    /**
     * 이력 목록 한 줄에 필요한 리비전 값이다.
     *
     * @param revisionNo 버전 번호
     * @param changeType 변경 종류 이름
     * @param createdAt 기록 시각
     * @param changedBy 변경한 사용자의 로그인 아이디 또는 null
     */
    record RevisionSummary(int revisionNo, String changeType, Instant createdAt, String changedBy) {
    }
}
