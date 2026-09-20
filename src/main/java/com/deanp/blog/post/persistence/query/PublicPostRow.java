package com.deanp.blog.post.persistence.query;

import java.time.Instant;
import java.util.UUID;

/**
 * 공개 게시글 조회 결과의 한 행과 선택된 태그명을 담는다.
 *
 * @param id 게시글 식별자
 * @param slug 게시글 슬러그
 * @param title 게시글 제목
 * @param summary 게시글 요약
 * @param content Markdown 원문 본문
 * @param publishedAt 게시글 발행 시각
 * @param tagName 연결된 태그명
 * @param seriesOrder 시리즈 안 게시글 순서
 * @param representativeImageName 대표 이미지 저장 파일명
 * @param categoryName 카테고리 표시명
 * @param seriesSlug 공개 시리즈 URL 값
 * @param seriesName 공개 시리즈 표시명
 */
public record PublicPostRow(
        UUID id,
        String slug,
        String title,
        String summary,
        String content,
        Instant publishedAt,
        String tagName,
        Integer seriesOrder,
        String representativeImageName,
        String categoryName, String seriesSlug, String seriesName
) {
    public PublicPostRow(UUID id, String slug, String title, String summary, String content,
                         Instant publishedAt, String tagName, Integer seriesOrder, String representativeImageName) {
        this(id, slug, title, summary, content, publishedAt, tagName, seriesOrder,
                representativeImageName, null, null, null);
    }
    public PublicPostRow(UUID id, String slug, String title, String summary, String content,
                         Instant publishedAt, String tagName) {
        this(id, slug, title, summary, content, publishedAt, tagName, null, null);
    }

    public PublicPostRow(UUID id, String slug, String title, String summary, String content,
                         Instant publishedAt, String tagName, String representativeImageName) {
        this(id, slug, title, summary, content, publishedAt, tagName, null, representativeImageName);
    }
}
