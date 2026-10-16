package com.deanp.blog.post.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 시리즈와 대표 카테고리를 blog.post_series 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_series", schema = "blog")
public class PostSeries {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "slug")
    private String slug;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private PostCategory category;

    /**
     * 새 시리즈 엔티티를 생성한다.
     *
     * @param categoryId 대표 카테고리 식별자
     * @param slug URL 식별자
     * @param name 표시 이름
     * @param description 소개 문구
     * @param sortOrder 목록 정렬 순서
     * @param status 상태: ACTIVE 연재 중, COMPLETED 완결, HIDDEN 숨김
     * @return 저장 가능한 시리즈 엔티티
     */
    public static PostSeries create(
            UUID categoryId,
            String slug,
            String name,
            String description,
            int sortOrder,
            String status
    ) {
        PostSeries series = new PostSeries();
        Instant now = Instant.now();
        series.id = UUID.randomUUID();
        series.categoryId = categoryId;
        series.slug = slug;
        series.name = name;
        series.description = description;
        series.sortOrder = sortOrder;
        series.status = status;
        series.createdAt = now;
        series.updatedAt = now;
        return series;
    }

    /**
     * 시리즈의 표시 정보, 대표 카테고리, 상태를 변경한다.
     *
     * @param categoryId 대표 카테고리 식별자
     * @param slug URL 식별자
     * @param name 표시 이름
     * @param description 소개 문구
     * @param sortOrder 목록 정렬 순서
     * @param status 상태: ACTIVE 연재 중, COMPLETED 완결, HIDDEN 숨김
     */
    public void update(UUID categoryId, String slug, String name, String description, int sortOrder, String status) {
        this.categoryId = categoryId;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
