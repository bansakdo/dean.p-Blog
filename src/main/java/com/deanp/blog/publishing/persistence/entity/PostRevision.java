package com.deanp.blog.publishing.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.auth.persistence.entity.AppUser;
import com.deanp.blog.post.persistence.entity.PostDetail;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 변경 시점의 본문 스냅샷을 blog.post_revision 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_revision", schema = "blog")
public class PostRevision {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "post_detail_id")
    private UUID postDetailId;

    @Column(name = "revision_no")
    private Integer revisionNo;

    @Column(name = "title")
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "content")
    private String content;

    @Column(name = "content_format")
    private String contentFormat;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "change_type")
    private String changeType;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_detail_id", insertable = false, updatable = false)
    private PostDetail postDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", insertable = false, updatable = false)
    private AppUser changedByUser;
}
