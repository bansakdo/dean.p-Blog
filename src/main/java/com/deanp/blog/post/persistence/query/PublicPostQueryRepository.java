package com.deanp.blog.post.persistence.query;

import java.util.List;

/**
 * 공개 게시글 목록과 상세 화면에 필요한 조회 전용 계약을 정의한다.
 */
public interface PublicPostQueryRepository {

    /**
     * 공개된 게시글 행을 최신 발행일 순으로 조회한다.
     *
     * @param categorySlug 카테고리 슬러그, 비어 있으면 전체 공개 글
     * @return 게시글과 태그명을 함께 담은 조회 행 목록
     */
    List<PublicPostRow> findPublishedRowsNewestFirst(String categorySlug);

    /**
     * 단일 슬러그에 해당하는 공개 게시글 행을 조회한다.
     *
     * @param slug 게시글 슬러그
     * @return 같은 게시글의 태그별 조회 행 목록
     */
    List<PublicPostRow> findPublishedRowsBySlug(String slug);
}
