package com.deanp.blog.tag.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * 게시글과 태그로 구성된 post_tag 복합 기본키를 표현한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTagId {
    @Column(name = "post_detail_id")
    private UUID postDetailId;
    @Column(name = "tag_id")
    private UUID tagId;

    /**
     * @param postDetailId 게시글 식별자
     * @param tagId 태그 식별자
     */
    public PostTagId(UUID postDetailId, UUID tagId) {
        this.postDetailId = postDetailId;
        this.tagId = tagId;
    }
}
