package com.deanp.blog.post.persistence.repository;

import com.deanp.blog.post.persistence.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 게시판 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface BoardRepository extends JpaRepository<Board, UUID> {

    /**
     * @param status 게시판 상태
     * @return 해당 상태의 게시판을 정렬 순서대로 나열한 목록
     */
    List<Board> findByStatusOrderBySortOrderAscNameAsc(String status);

    /**
     * @param slug 게시판 주소
     * @param status 게시판 상태
     * @return 주소와 상태가 일치하는 게시판
     */
    Optional<Board> findBySlugAndStatus(String slug, String status);

    /**
     * @param id 게시판 식별자
     * @param status 게시판 상태
     * @return 식별자와 상태가 일치하는 게시판이 있으면 true
     */
    boolean existsByIdAndStatus(UUID id, String status);
}
