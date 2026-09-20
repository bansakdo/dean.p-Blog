package com.deanp.blog.post;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 서버 렌더링 화면에서 사용하는 공개 게시글 표시 데이터를 담는다.
 *
 * @param id 게시글 상세 식별자
 * @param slug 게시글 슬러그
 * @param title 게시글 제목
 * @param summary 게시글 요약
 * @param publishedAt 게시글 발행일
 * @param readingMinutes 예상 읽기 시간(분)
 * @param tags 게시글 태그 목록
 * @param htmlContent Markdown을 HTML로 변환한 본문
 * @param representativeImageUrl 대표 이미지 주소
 * @param categoryName 카테고리 표시명
 * @param seriesSlug 공개 시리즈 URL 값
 * @param seriesName 공개 시리즈 표시명
 * @param seriesOrder 저장된 연재 순서 (필터 결과의 행 번호가 아님)
 */
public record PostView(
        UUID id,
        String slug,
        String title,
        String summary,
        LocalDate publishedAt,
        int readingMinutes,
        List<String> tags,
        String htmlContent,
        String representativeImageUrl,
        String categoryName, String seriesSlug, String seriesName, Integer seriesOrder
) {
    public PostView(UUID id, String slug, String title, String summary, LocalDate publishedAt,
                    int readingMinutes, List<String> tags, String htmlContent, String representativeImageUrl) {
        this(id, slug, title, summary, publishedAt, readingMinutes, tags, htmlContent,
                representativeImageUrl, null, null, null, null);
    }
    public PostView(UUID id, String slug, String title, String summary, LocalDate publishedAt,
                    int readingMinutes, List<String> tags, String htmlContent) {
        this(id, slug, title, summary, publishedAt, readingMinutes, tags, htmlContent, null);
    }
}
