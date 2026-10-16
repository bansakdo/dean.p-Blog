package com.deanp.blog.post.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 글 관리 목록의 상태 필터다. 기본값은 삭제한 글을 뺀 전체 글이다.
 */
public enum AdminPostFilter {

    /** 초안과 공개 글 */
    ALL("", List.of("DRAFT", "PUBLISHED")),
    /** 초안 */
    DRAFT("draft", List.of("DRAFT")),
    /** 공개 글 */
    PUBLISHED("published", List.of("PUBLISHED")),
    /** 삭제한 글 */
    DELETED("deleted", List.of("DELETED"));

    private final String param;
    private final List<String> statuses;

    /**
     * @param param 주소의 status 파라미터 값
     * @param statuses 이 필터에 포함되는 글 상태
     */
    AdminPostFilter(String param, List<String> statuses) {
        this.param = param;
        this.statuses = statuses;
    }

    /**
     * @param param 주소의 status 파라미터 값
     * @return 일치하는 필터, 모르는 값이면 빈 값
     */
    public static Optional<AdminPostFilter> fromParam(String param) {
        return Arrays.stream(values()).filter(filter -> filter.param.equals(param)).findFirst();
    }

    /** @return 주소의 status 파라미터 값 */
    public String getParam() {
        return param;
    }

    /** @return 이 필터에 포함되는 글 상태 */
    public List<String> getStatuses() {
        return statuses;
    }
}
