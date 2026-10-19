package com.deanp.blog.tag.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 분류와 탐색에 사용할 태그 정보를 blog.tag 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tag", schema = "blog")
public class Tag {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "slug")
    private String slug;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 새 태그를 만든다.
     *
     * @param name 표시 이름
     * @param slug URL 식별자
     * @return 저장할 태그
     */
    public static Tag create(String name, String slug) {
        Tag tag = new Tag();
        Instant now = Instant.now();
        tag.id = UUID.randomUUID();
        tag.name = name;
        tag.slug = slug;
        tag.createdAt = now;
        tag.updatedAt = now;
        return tag;
    }

    /**
     * 태그의 이름과 주소를 바꾼다.
     *
     * @param name 표시 이름
     * @param slug URL 식별자
     */
    public void rename(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.updatedAt = Instant.now();
    }
}
