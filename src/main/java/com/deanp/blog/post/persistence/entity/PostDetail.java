package com.deanp.blog.post.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.auth.persistence.entity.AppUser;
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
 * 공개 글 본문과 게시 상태를 blog.post_detail 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_detail", schema = "blog")
public class PostDetail {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "slug")
    private String slug;

    @Column(name = "title")
    private String title;

    @Column(name = "summary")
    private String summary;

    @Column(name = "content")
    private String content;

    @Column(name = "content_format")
    private String contentFormat;

    @Column(name = "status")
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private PostCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private AppUser author;
}
