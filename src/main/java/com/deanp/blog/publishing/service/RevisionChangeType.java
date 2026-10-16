package com.deanp.blog.publishing.service;

/**
 * 게시글 리비전을 남긴 변경의 종류다. 이름을 post_revision.change_type에 저장한다.
 */
public enum RevisionChangeType {

    /** 이력 기록을 시작하기 전 마지막으로 저장된 상태 */
    BASELINE("기록 시작 전"),
    /** 새 글 작성 */
    CREATE("작성"),
    /** 공개 상태를 바꾸지 않은 저장 */
    UPDATE("저장"),
    /** 발행 */
    PUBLISH("발행"),
    /** 비공개 전환 */
    UNPUBLISH("비공개 전환"),
    /** 삭제 */
    DELETE("삭제"),
    /** 삭제한 글 복구 */
    RESTORE("복구"),
    /** 이전 버전으로 되돌림 */
    REVERT("되돌림");

    private final String label;

    /** @param label 화면에 보일 이름 */
    RevisionChangeType(String label) {
        this.label = label;
    }

    /**
     * @param name 저장된 변경 종류 이름
     * @return 화면에 보일 이름, 모르는 값이면 저장된 이름 그대로
     */
    public static String labelOf(String name) {
        for (RevisionChangeType type : values()) {
            if (type.name().equals(name)) {
                return type.label;
            }
        }
        return name;
    }
}
