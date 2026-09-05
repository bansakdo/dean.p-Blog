package com.deanp.blog.visitor.persistence.entity;

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
 * 익명 방문자 식별자와 방문 시각을 blog.visitor 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "visitor", schema = "blog")
public class Visitor {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "anonymous_key")
    private String anonymousKey;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * 새 익명 방문자 엔티티를 초기 방문 시각과 함께 생성한다.
     *
     * @param anonymousKey 쿠키로 식별한 익명 방문자 UUID 문자열
     * @param seenAt 최초 방문 시각
     * @return 저장 가능한 방문자 엔티티
     */
    public static Visitor create(String anonymousKey, Instant seenAt) {
        Visitor visitor = new Visitor();
        visitor.id = UUID.randomUUID();
        visitor.anonymousKey = anonymousKey;
        visitor.firstSeenAt = seenAt;
        visitor.lastSeenAt = seenAt;
        visitor.createdAt = seenAt;
        return visitor;
    }

    /**
     * 방문자의 마지막 확인 시각을 갱신한다.
     *
     * @param seenAt 새 마지막 방문 시각
     */
    public void markSeen(Instant seenAt) {
        this.lastSeenAt = seenAt;
    }
}
