package com.deanp.blog.post;

/**
 * 공개 글 필터의 URL 값과 화면 이름을 담는다.
 * @param slug 필터 URL 값
 * @param name 화면 표시 이름
 * @param postCount 전체 공개 글 중 이 분류에 속한 글 수
 */
public record PostFilterOption(String slug, String name, long postCount) {
    public PostFilterOption(String slug, String name) {
        this(slug, name, 0);
    }
}
