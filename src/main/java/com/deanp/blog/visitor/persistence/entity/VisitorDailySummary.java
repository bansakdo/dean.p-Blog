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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "visitor_daily_summary", schema = "blog")
public class VisitorDailySummary {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "summary_date")
    private LocalDate summaryDate;

    @Column(name = "board_detail_id")
    private UUID boardDetailId;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "search_engine")
    private String searchEngine;

    @Column(name = "landing_count")
    private Long landingCount;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "unique_visitor_count")
    private Long uniqueVisitorCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_detail_id", insertable = false, updatable = false)
    private BoardDetail boardDetail;
}
