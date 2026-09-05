package com.deanp.blog.tag.persistence.entity;

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
 * 게시글과 태그의 연결을 blog.post_tag 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_tag", schema = "blog")
public class PostTag {

    @EmbeddedId
    private PostTagId id;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_detail_id", insertable = false, updatable = false)
    private PostDetail postDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private Tag tag;
}
