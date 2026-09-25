package com.deanp.blog.post.service;

import java.util.UUID;

/**
 * 시리즈 생성과 수정에 필요한 입력 값을 담는다.
 *
 * @param categoryId 대표 카테고리 식별자
 * @param slug URL 식별자
 * @param name 표시 이름
 * @param description 소개 문구
 * @param sortOrder 목록 정렬 순서
 */
public record PostSeriesCommand(
        UUID categoryId,
        String slug,
        String name,
        String description,
        int sortOrder
) {

    /**
     * 생성 요청의 필수 값을 검증하고 문자열 값을 정리한다.
     *
     * @return 정규화된 명령
     */
    public PostSeriesCommand normalized() {
        String normalizedSlug = requireText(slug, "시리즈 slug는 필수입니다.");
        String normalizedName = requireText(name, "시리즈 이름은 필수입니다.");
        return new PostSeriesCommand(
                categoryId,
                normalizedSlug,
                normalizedName,
                blankToNull(description),
                sortOrder
        );
    }

    /**
     * 필수 문자열을 검증하고 좌우 공백을 제거한다.
     *
     * @param value 입력 문자열
     * @param message 실패 메시지
     * @return 정리된 문자열
     */
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    /**
     * 선택 문자열의 공백 값을 null로 바꾼다.
     *
     * @param value 입력 문자열
     * @return 정리된 문자열 또는 null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
