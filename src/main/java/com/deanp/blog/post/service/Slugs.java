package com.deanp.blog.post.service;

import java.util.Locale;

/**
 * 카테고리·시리즈·태그 주소(slug)를 글 주소와 같은 금지 문자 규칙으로 만들고 검사한다.
 */
public final class Slugs {

    /** 인스턴스를 만들지 않는다. */
    private Slugs() {
    }

    /**
     * 입력한 주소를 검사하고, 비어 있으면 이름을 소문자로 바꾼 뒤 금지 문자를 지우고 공백을 하이픈으로 바꿔 만든다.
     *
     * @param slug 입력한 주소 또는 빈 값
     * @param name 주소가 비었을 때 쓸 이름
     * @param maxLength 최대 길이
     * @return 정리된 주소
     */
    public static String of(String slug, String name, int maxLength) {
        String normalized;
        if (slug == null || slug.isBlank()) {
            String source = PostEditCommand.SLUG_FORBIDDEN.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("").strip();
            normalized = PostEditCommand.WHITESPACE.matcher(source).replaceAll("-");
        } else {
            normalized = slug.strip();
            if (PostEditCommand.SLUG_FORBIDDEN.matcher(normalized).find() || PostEditCommand.WHITESPACE.matcher(normalized).find()) {
                throw new IllegalArgumentException("주소(slug)에는 공백과 / ? # % & \\ \" ' < > 문자를 쓸 수 없습니다.");
            }
        }
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("주소(slug)는 1자 이상 " + maxLength + "자 이하여야 합니다.");
        }
        return normalized;
    }

    /**
     * 필수 이름을 검사하고 좌우 공백을 지운다.
     *
     * @param name 입력한 이름
     * @param maxLength 최대 길이
     * @return 정리된 이름
     */
    public static String name(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 필수입니다.");
        }
        String normalized = name.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("이름은 " + maxLength + "자를 넘을 수 없습니다.");
        }
        return normalized;
    }
}
