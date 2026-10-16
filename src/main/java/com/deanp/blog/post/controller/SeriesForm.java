package com.deanp.blog.post.controller;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 시리즈 작성·수정 폼의 입력 값을 받는다.
 */
@Getter
@Setter
public class SeriesForm {

    private UUID categoryId;

    private String name;

    private String slug;

    private String description;

    private int sortOrder;

    /** 상태: ACTIVE 연재 중, COMPLETED 완결, HIDDEN 숨김 */
    private String status = "ACTIVE";
}
