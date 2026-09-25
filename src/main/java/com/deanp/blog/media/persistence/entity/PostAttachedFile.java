package com.deanp.blog.media.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.post.persistence.entity.PostDetail;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 게시글과 첨부 파일의 연결을 blog.post_attached_file 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_attached_file", schema = "blog")
public class PostAttachedFile {

    @EmbeddedId
    private PostAttachedFileId id;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_detail_id", insertable = false, updatable = false)
    private PostDetail postDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_file_id", insertable = false, updatable = false)
    private AttachedFile attachedFile;
}
