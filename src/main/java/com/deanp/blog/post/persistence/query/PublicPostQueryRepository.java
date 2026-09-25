package com.deanp.blog.post.persistence.query;

import java.util.List;
import com.deanp.blog.post.PostFilterOption;
import com.deanp.blog.post.PostSeriesOption;

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

    /** 카테고리와 태그 조건을 모두 만족하는 공개 글의 전체 태그 행을 반환한다.
     * @param categorySlug 선택 카테고리
     * @param tagSlug 선택 태그
     * @return 최신순 공개 글 행
     */
    List<PublicPostRow> findPublishedRowsNewestFirst(String categorySlug, String tagSlug);

    /**
     * 카테고리, 시리즈, 태그, 검색어 조건을 모두 만족하는 공개 글의 전체 태그 행을 반환한다.
     *
     * @param categorySlug 선택 카테고리
     * @param seriesSlug 선택 시리즈
     * @param tagSlug 선택 태그
     * @param search 검색어
     * @return 공개 글 행
     */
    List<PublicPostRow> findPublishedRows(String categorySlug, String seriesSlug, String tagSlug, String search);

    /** @return 미분류 글까지 포함한 전체 공개 글 수 */
    long countPublishedPosts();

    /** @return 공개 글에 사용된 카테고리 선택지 */
    List<PostFilterOption> findPublishedCategories();

    /** @return 공개 글에 사용된 태그 선택지 */
    List<PostFilterOption> findPublishedTags();

    /**
     * 공개 글이 있는 시리즈 선택지를 조회한다.
     *
     * @param categorySlug 선택 카테고리
     * @return 시리즈 선택지
     */
    List<PostSeriesOption> findPublishedSeries(String categorySlug);

    /**
     * 공개 글이 있는 시리즈 소개 정보를 조회한다.
     *
     * @param categorySlug 선택 카테고리
     * @param seriesSlug 선택 시리즈
     * @return 일치하는 공개 시리즈
     */
    java.util.Optional<PostSeriesOption> findPublishedSeries(String categorySlug, String seriesSlug);

    /**
     * 단일 슬러그에 해당하는 공개 게시글 행을 조회한다.
     *
     * @param slug 게시글 슬러그
     * @return 같은 게시글의 태그별 조회 행 목록
     */
    List<PublicPostRow> findPublishedRowsBySlug(String slug);
}
