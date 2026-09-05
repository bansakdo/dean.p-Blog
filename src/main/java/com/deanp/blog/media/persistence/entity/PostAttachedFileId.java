package com.deanp.blog.media.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * 게시글과 첨부 파일로 구성된 post_attached_file 복합 기본키를 표현한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostAttachedFileId {
    @Column(name = "post_detail_id")
    private UUID postDetailId;
    @Column(name = "attached_file_id")
    private UUID attachedFileId;
}
