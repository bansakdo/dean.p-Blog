package com.deanp.blog.post;

/**
 * 공개 글 목록에서 표시할 시리즈 필터와 소개 정보를 담는다.
 *
 * @param slug 시리즈 URL 식별자
 * @param name 시리즈 표시 이름
 * @param description 시리즈 소개 문구
 * @param categorySlug 대표 카테고리 URL 식별자
 * @param postCount 공개 글 편수 (초안·예약 글 제외)
 * @param status 저장된 상태: ACTIVE 연재 중, COMPLETED 완결
 */
public record PostSeriesOption(String slug, String name, String description, String categorySlug,
                               long postCount, String status) {
    public PostSeriesOption(String slug, String name, String description, String categorySlug) {
        this(slug, name, description, categorySlug, 0, "ACTIVE");
    }

    /** @return 저장된 시리즈 상태의 화면 표시명 */
    public String statusLabel() {
        return "COMPLETED".equals(status) ? "완결" : "연재 중";
    }
}
