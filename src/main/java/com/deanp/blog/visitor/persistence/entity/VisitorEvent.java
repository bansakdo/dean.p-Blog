package com.deanp.blog.visitor.persistence.entity;

import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.visitor.service.VisitorRequestMetadata;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 방문자의 글 조회와 유입 정보를 blog.visitor_event 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "visitor_event", schema = "blog")
public class VisitorEvent {

    /** 게시글 상세 조회 이벤트 유형이다. */
    public static final String POST_VIEW = "POST_VIEW";

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "visitor_id")
    private UUID visitorId;

    @Column(name = "post_detail_id")
    private UUID postDetailId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "referrer_host")
    private String referrerHost;

    @Column(name = "search_engine")
    private String searchEngine;

    @Column(name = "search_query")
    private String searchQuery;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "request_id")
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitor_id", insertable = false, updatable = false)
    private Visitor visitor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_detail_id", insertable = false, updatable = false)
    private PostDetail postDetail;

    /**
     * 게시글 상세 조회 이벤트를 생성한다.
     *
     * @param visitorId 방문자 식별자
     * @param postDetailId 조회한 게시글 상세 식별자
     * @param occurredAt 이벤트 발생 시각
     * @param metadata 요청에서 수집한 메타데이터
     * @return 저장 가능한 방문 이벤트 엔티티
     */
    public static VisitorEvent postView(
            UUID visitorId,
            UUID postDetailId,
            Instant occurredAt,
            VisitorRequestMetadata metadata
    ) {
        VisitorEvent event = new VisitorEvent();
        event.id = UUID.randomUUID();
        event.visitorId = visitorId;
        event.postDetailId = postDetailId;
        event.eventType = POST_VIEW;
        event.occurredAt = occurredAt;
        event.ipAddress = metadata.ipAddress();
        event.referrerHost = metadata.referrerHost();
        event.userAgent = metadata.userAgent();
        event.requestId = metadata.requestId();
        return event;
    }
}
