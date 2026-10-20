package com.deanp.blog.post.service;

import com.deanp.blog.post.persistence.entity.Board;
import com.deanp.blog.post.persistence.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 공개 화면과 관리 화면이 쓰는 활성 게시판(개발·노트)을 화면용 값으로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class BoardService {

    private static final String ACTIVE = "ACTIVE";

    private final BoardRepository boards;

    /**
     * @return 활성 게시판을 정렬 순서대로 나열한 목록. 첫 게시판이 기본 게시판이다
     */
    @Transactional(readOnly = true)
    public List<BoardView> findActive() {
        return boards.findByStatusOrderBySortOrderAscNameAsc(ACTIVE).stream().map(BoardView::of).toList();
    }

    /**
     * @param slug 게시판 주소
     * @return 주소가 일치하는 활성 게시판
     */
    @Transactional(readOnly = true)
    public Optional<BoardView> findActive(String slug) {
        return boards.findBySlugAndStatus(slug, ACTIVE).map(BoardView::of);
    }

    /**
     * 게시판의 화면 표시 값이다.
     *
     * @param id 게시판 식별자
     * @param slug 게시판 주소
     * @param name 게시판 이름
     * @param description 목록 화면 소개 문구
     */
    public record BoardView(UUID id, String slug, String name, String description) {

        /**
         * @param board 게시판 엔티티
         * @return 화면 표시 값
         */
        static BoardView of(Board board) {
            return new BoardView(board.getId(), board.getSlug(), board.getName(), board.getDescription());
        }

        /**
         * @return 게시판 목록 주소
         */
        public String path() {
            return "/" + slug;
        }
    }
}
