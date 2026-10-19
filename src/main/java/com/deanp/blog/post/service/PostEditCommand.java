package com.deanp.blog.post.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 글 작성과 수정에 필요한 입력 값을 담는다.
 *
 * @param title 제목
 * @param slug URL 식별자, 비어 있으면 제목에서 만든다
 * @param summary 요약
 * @param content Markdown 본문
 * @param categoryId 카테고리 식별자 또는 null
 * @param tagNames 태그 이름 목록
 * @param representativeImageId 대표 이미지 파일 식별자 또는 null
 * @param action 저장 후 공개 상태를 어떻게 할지
 * @param publishAt 지정한 발행 시각 또는 null, 미래 시각이면 예약 발행
 */
public record PostEditCommand(
        String title,
        String slug,
        String summary,
        String content,
        UUID categoryId,
        List<String> tagNames,
        UUID representativeImageId,
        PublishAction action,
        Instant publishAt
) {

    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_SLUG_LENGTH = 200;
    private static final int MAX_TAG_LENGTH = 100;
    static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** 경로·쿼리 구분자와 HTML 특수문자는 주소에 쓰지 않는다. 태그 주소도 같은 규칙을 따른다. */
    static final Pattern SLUG_FORBIDDEN = Pattern.compile("[/?#%&\\\\\"'<>]");

    /**
     * 저장 후 글의 공개 상태를 정한다.
     */
    public enum PublishAction {
        /** 현재 상태를 유지한다. 새 글은 초안이 된다. */
        KEEP,
        /** 글을 공개한다. */
        PUBLISH,
        /** 글을 초안으로 되돌린다. */
        UNPUBLISH
    }

    /**
     * 필수 값을 검증하고 문자열을 정리한다. slug가 비어 있으면 제목에서 만든다.
     *
     * @return 정규화된 명령
     */
    public PostEditCommand normalized() {
        // 제목과 본문은 필수이고 제목은 컬럼 길이를 넘을 수 없다.
        String normalizedTitle = requireText(title, "제목은 필수입니다.");
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + MAX_TITLE_LENGTH + "자를 넘을 수 없습니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("본문은 필수입니다.");
        }

        // 주소는 입력값을 우선하고, 없으면 제목의 공백을 하이픈으로 바꿔 만든다.
        String source = slug == null || slug.isBlank() ? normalizedTitle : slug.strip();
        String normalizedSlug = WHITESPACE.matcher(SLUG_FORBIDDEN.matcher(source).replaceAll("")).replaceAll("-");
        if (slug != null && !slug.isBlank() && !normalizedSlug.equals(slug.strip())) {
            throw new IllegalArgumentException("주소(slug)에는 공백과 / ? # % & \\ \" ' < > 문자를 쓸 수 없습니다.");
        }
        if (normalizedSlug.isEmpty() || normalizedSlug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException("주소(slug)는 1자 이상 " + MAX_SLUG_LENGTH + "자 이하여야 합니다.");
        }

        // 태그는 빈 값과 대소문자만 다른 중복을 제거하고 입력 순서를 유지한다.
        List<String> normalizedTags = tagNames == null ? List.of() : tagNames.stream()
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(java.util.stream.Collectors.toMap(String::toLowerCase, name -> name,
                        (first, second) -> first, java.util.LinkedHashMap::new))
                .values().stream().toList();
        if (normalizedTags.stream().anyMatch(name -> name.length() > MAX_TAG_LENGTH)) {
            throw new IllegalArgumentException("태그는 " + MAX_TAG_LENGTH + "자를 넘을 수 없습니다.");
        }

        return new PostEditCommand(
                normalizedTitle,
                normalizedSlug,
                summary == null || summary.isBlank() ? null : summary.strip(),
                content,
                categoryId,
                normalizedTags,
                representativeImageId,
                action == null ? PublishAction.KEEP : action,
                publishAt
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
}
