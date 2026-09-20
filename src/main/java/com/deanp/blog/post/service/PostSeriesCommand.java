package com.deanp.blog.post.service;

import java.util.UUID;

/**
 * 시리즈 생성과 수정에 필요한 입력 값을 담는다.
 *
 * @param postId 게시판 식별자
 * @param categoryId 대표 카테고리 식별자
 * @param slug URL 식별자
 * @param name 표시 이름
 * @param description 소개 문구
 * @param sortOrder 목록 정렬 순서
 */
public record PostSeriesCommand(
        UUID postId,
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
        if (postId == null) {
            throw new IllegalArgumentException("게시판 식별자는 필수입니다.");
        }
        return normalizedForPost(postId);
    }

    /**
     * 기존 게시판 식별자를 기준으로 수정 요청 값을 정리한다.
     *
     * @param existingPostId 기존 시리즈의 게시판 식별자
     * @return 정규화된 명령
     */
    public PostSeriesCommand normalizedForPost(UUID existingPostId) {
        String normalizedSlug = requireText(slug, "시리즈 slug는 필수입니다.");
        String normalizedName = requireText(name, "시리즈 이름은 필수입니다.");
        return new PostSeriesCommand(
                existingPostId,
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
