package com.deanp.blog.post.persistence.entity;

import jakarta.persistence.Column;
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
 * 게시글 분류 정보를 blog.post_category 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_category", schema = "blog")
public class PostCategory {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "board_id")
    private UUID boardId;

    @Column(name = "name")
    private String name;

    @Column(name = "slug")
    private String slug;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private PostCategory parent;

    /**
     * 최상위 활성 카테고리를 만든다.
     *
     * @param boardId 속한 게시판 식별자
     * @param name 표시 이름
     * @param slug URL 식별자
     * @param sortOrder 목록 정렬 순서
     * @return 저장할 카테고리
     */
    public static PostCategory create(UUID boardId, String name, String slug, int sortOrder) {
        PostCategory category = new PostCategory();
        Instant now = Instant.now();
        category.id = UUID.randomUUID();
        category.boardId = boardId;
        category.name = name;
        category.slug = slug;
        category.sortOrder = sortOrder;
        category.status = "ACTIVE";
        category.createdAt = now;
        category.updatedAt = now;
        return category;
    }

    /**
     * 카테고리의 이름, 주소, 정렬 순서를 바꾼다.
     *
     * @param name 표시 이름
     * @param slug URL 식별자
     * @param sortOrder 목록 정렬 순서
     */
    public void update(String name, String slug, int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    /**
     * 카테고리를 다른 게시판으로 옮긴다. 이 카테고리의 글도 함께 옮기는 것은 호출하는 서비스가 맡는다.
     *
     * @param boardId 새 게시판 식별자
     */
    public void changeBoard(UUID boardId) {
        this.boardId = boardId;
        this.updatedAt = Instant.now();
    }
}
