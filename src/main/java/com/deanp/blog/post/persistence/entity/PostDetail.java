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

    /**
     * 아직 공개되지 않은 Markdown 초안을 만든다.
     *
     * @param authorId 작성자 식별자
     * @param categoryId 카테고리 식별자 또는 null
     * @param slug URL 식별자
     * @param title 제목
     * @param summary 요약 또는 null
     * @param content Markdown 본문
     * @return 저장할 초안 게시글
     */
    public static PostDetail createDraft(UUID authorId, UUID categoryId, String slug, String title,
                                         String summary, String content) {
        PostDetail post = new PostDetail();
        Instant now = Instant.now();
        post.id = UUID.randomUUID();
        post.authorId = authorId;
        post.categoryId = categoryId;
        post.slug = slug;
        post.title = title;
        post.summary = summary;
        post.content = content;
        post.contentFormat = "MARKDOWN";
        post.status = "DRAFT";
        post.createdAt = now;
        post.updatedAt = now;
        return post;
    }

    /**
     * 글의 주소, 제목, 요약, 본문을 고친다.
     *
     * @param slug URL 식별자
     * @param title 제목
     * @param summary 요약 또는 null
     * @param content Markdown 본문
     */
    public void edit(String slug, String title, String summary, String content) {
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    /**
     * 대표 이미지를 바꾼다. 이 글에 첨부된 파일만 지정할 수 있다는 제약은 DB 외래키가 지킨다.
     *
     * @param representativeImageId 대표 이미지 파일 식별자 또는 null
     */
    public void changeRepresentativeImage(UUID representativeImageId) {
        this.representativeImageId = representativeImageId;
        this.updatedAt = Instant.now();
    }

    /**
     * 글을 공개한다. 발행 시각을 지정하면 그 시각을 쓰고, 미래 시각이면 그때까지 공개 화면에 나오지 않는다.
     * 지정하지 않으면 처음 공개할 때만 지금을 기록해 재발행해도 원래 발행일을 유지한다. 다만 남아 있던
     * 발행 시각이 미래이면 예약된 채로 남지 않도록 지금으로 바꾼다.
     *
     * @param publishAt 지정한 발행 시각 또는 null
     */
    public void publish(Instant publishAt) {
        Instant now = Instant.now();
        this.status = "PUBLISHED";
        if (publishAt != null) {
            this.publishedAt = publishAt;
        } else if (this.publishedAt == null || this.publishedAt.isAfter(now)) {
            this.publishedAt = now;
        }
        this.updatedAt = now;
    }

    /**
     * 공개 중이거나 예약한 글의 발행 시각을 바꾼다.
     *
     * @param publishAt 새 발행 시각
     */
    public void reschedule(Instant publishAt) {
        this.publishedAt = publishAt;
        this.updatedAt = Instant.now();
    }

    /**
     * 글을 초안으로 되돌려 공개 화면에서 내린다. 발행 시각은 지우지 않는다.
     */
    public void unpublish() {
        this.status = "DRAFT";
        this.updatedAt = Instant.now();
    }

    /**
     * 글을 삭제 상태로 바꿔 공개 화면과 기본 관리 목록에서 숨긴다. 행과 첨부, 태그 연결은 복구할 수 있도록 남긴다.
     */
    public void delete() {
        this.status = "DELETED";
        this.updatedAt = Instant.now();
    }

    /**
     * 삭제한 글을 초안으로 되돌린다. 복구하자마자 공개되지 않도록 다시 발행해야 공개된다.
     */
    public void restore() {
        this.status = "DRAFT";
        this.updatedAt = Instant.now();
    }
}
