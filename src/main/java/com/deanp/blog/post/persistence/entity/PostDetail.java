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

    @Column(name = "representative_image_id")
    private UUID representativeImageId;

    @Column(name = "series_id")
    private UUID seriesId;

    @Column(name = "series_order")
    private Integer seriesOrder;

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
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private PostCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false)
    private PostSeries series;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private AppUser author;

    /**
     * 게시글을 시리즈에 배치하고 시리즈 대표 카테고리를 게시글 카테고리로 맞춘다.
     *
     * @param series 배치할 시리즈
     * @param seriesOrder 시리즈 안에서의 순서
     */
    public void assignToSeries(PostSeries series, int seriesOrder) {
        this.seriesId = series.getId();
        this.seriesOrder = seriesOrder;
        this.categoryId = series.getCategoryId();
        this.updatedAt = Instant.now();
    }

    /**
     * 시리즈 연결을 해제하고 현재 카테고리는 유지한다.
     */
    public void removeFromSeries() {
        this.seriesId = null;
        this.seriesOrder = null;
        this.updatedAt = Instant.now();
    }

    /**
     * 연결된 시리즈의 대표 카테고리 변경을 게시글에 반영한다.
     *
     * @param categoryId 새 카테고리 식별자
     */
    public void changeCategory(UUID categoryId) {
        this.categoryId = categoryId;
        this.updatedAt = Instant.now();
    }
}
