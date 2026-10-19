package com.deanp.blog.post.service;

import java.util.Set;
import java.util.UUID;

/**
 * 시리즈 생성과 수정에 필요한 입력 값을 담는다.
 *
 * @param categoryId 대표 카테고리 식별자
 * @param slug URL 식별자
 * @param name 표시 이름
 * @param description 소개 문구
 * @param sortOrder 목록 정렬 순서
 * @param status 상태: ACTIVE 연재 중, COMPLETED 완결, HIDDEN 숨김. 비어 있으면 연재 중
 */
public record PostSeriesCommand(
        UUID categoryId,
        String slug,
        String name,
        String description,
        int sortOrder,
        String status
) {

    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_SLUG_LENGTH = 150;
    private static final Set<String> STATUSES = Set.of("ACTIVE", "COMPLETED", "HIDDEN");

    /**
     * 필수 값을 검증하고 문자열 값을 정리한다. slug가 비어 있으면 이름으로 만든다.
     *
     * @return 정규화된 명령
     */
    public PostSeriesCommand normalized() {
        String normalizedName = requireText(name, "시리즈 이름은 필수입니다.");
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("시리즈 이름은 " + MAX_NAME_LENGTH + "자를 넘을 수 없습니다.");
        }
        String normalizedStatus = status == null || status.isBlank() ? "ACTIVE" : status.strip();
        if (!STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("알 수 없는 시리즈 상태입니다.");
        }
        return new PostSeriesCommand(
                categoryId,
                Slugs.of(slug, normalizedName, MAX_SLUG_LENGTH),
                normalizedName,
                blankToNull(description),
                sortOrder,
                normalizedStatus
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
