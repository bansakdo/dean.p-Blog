package com.deanp.blog.visitor.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.post.persistence.entity.BoardDetail;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "visitor_event", schema = "blog")
public class VisitorEvent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "visitor_id")
    private UUID visitorId;

    @Column(name = "board_detail_id")
    private UUID boardDetailId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "country_code")
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
    @JoinColumn(name = "board_detail_id", insertable = false, updatable = false)
    private BoardDetail boardDetail;
}
