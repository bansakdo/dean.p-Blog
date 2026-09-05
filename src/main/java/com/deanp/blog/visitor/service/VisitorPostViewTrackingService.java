package com.deanp.blog.visitor.service;

import com.deanp.blog.visitor.persistence.entity.Visitor;
import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import com.deanp.blog.visitor.persistence.repository.VisitorDailySummaryRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorTrackingLockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 공개 게시글 상세 조회를 익명 방문자와 방문 이벤트로 저장한다.
 */
@Service
public class VisitorPostViewTrackingService {

    private final VisitorRepository visitors;
    private final VisitorEventRepository events;
    private final VisitorDailySummaryRepository dailySummaries;
    private final VisitorTrackingLockRepository trackingLocks;
    private final Clock clock;

    /**
     * 운영 환경에서 UTC 시스템 시계를 사용하는 방문 이벤트 저장 서비스를 구성한다.
     *
     * @param visitors 방문자 저장소
     * @param events 방문 이벤트 저장소
     * @param dailySummaries 일별 방문 집계 저장소
     * @param trackingLocks 방문자 집계 advisory lock 저장소
     */
    @Autowired
    public VisitorPostViewTrackingService(
            VisitorRepository visitors,
            VisitorEventRepository events,
            VisitorDailySummaryRepository dailySummaries,
            VisitorTrackingLockRepository trackingLocks
    ) {
        this(visitors, events, dailySummaries, trackingLocks, Clock.systemUTC());
    }

    /**
     * 테스트에서 고정 시계를 주입할 수 있는 방문 이벤트 저장 서비스를 구성한다.
     *
     * @param visitors 방문자 저장소
     * @param events 방문 이벤트 저장소
     * @param dailySummaries 일별 방문 집계 저장소
     * @param trackingLocks 방문자 집계 advisory lock 저장소
     * @param clock 이벤트 발생 시각을 제공할 시계
     */
    VisitorPostViewTrackingService(
            VisitorRepository visitors,
            VisitorEventRepository events,
            VisitorDailySummaryRepository dailySummaries,
            VisitorTrackingLockRepository trackingLocks,
            Clock clock
    ) {
        this.visitors = visitors;
        this.events = events;
        this.dailySummaries = dailySummaries;
        this.trackingLocks = trackingLocks;
        this.clock = clock;
    }

    /**
     * 방문자를 조회하거나 생성하고 게시글 상세 조회 이벤트를 저장한다.
     *
     * @param anonymousKey 쿠키에서 식별한 익명 방문자 UUID 문자열
     * @param postDetailId 조회에 성공한 게시글 상세 식별자
     * @param metadata 요청에서 수집한 저장 가능한 메타데이터
     */
    @Transactional
    public void recordPostView(String anonymousKey, UUID postDetailId, VisitorRequestMetadata metadata) {
        Instant occurredAt = Instant.now(clock);
        LocalDate summaryDate = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();

        // lock 순서는 모든 요청에서 visitor -> summary로 고정해 deadlock 가능성을 줄인다.
        trackingLocks.lockVisitor(anonymousKey);
        Visitor visitor = visitors.findByAnonymousKey(anonymousKey)
                .map(existing -> {
                    existing.markSeen(occurredAt);
                    return existing;
                })
                .orElseGet(() -> visitors.save(Visitor.create(anonymousKey, occurredAt)));

        trackingLocks.lockDailySummary(summaryDate, postDetailId);

        // 원본 이벤트를 먼저 저장한 뒤 같은 트랜잭션에서 일별 요약 집계가 참조할 수 있도록 flush한다.
        events.saveAndFlush(VisitorEvent.postView(visitor.getId(), postDetailId, occurredAt, metadata));

        // 현재 집계 범위는 UTC 날짜와 게시글 상세 식별자이며 landing_count는 상세 진입 이벤트 수와 동일하게 증가한다.
        dailySummaries.upsertPostViewSummary(
                UUID.randomUUID(),
                summaryDate,
                postDetailId,
                occurredAt
        );
    }
}
