package com.deanp.blog.post.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 글 작성·수정 폼의 입력 값을 받는다.
 */
@Getter
@Setter
public class PostForm {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 300, message = "제목은 300자를 넘을 수 없습니다.")
    private String title;

    @Size(max = 200, message = "주소(slug)는 200자를 넘을 수 없습니다.")
    private String slug;

    private String summary;

    @NotBlank(message = "본문은 필수입니다.")
    private String content;

    private UUID categoryId;

    private String tags;

    private UUID representativeImageId;
}
