package com.deanp.blog.visitor.service;

import com.deanp.blog.visitor.persistence.entity.Visitor;
import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import com.deanp.blog.visitor.persistence.repository.VisitorDailySummaryRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorTrackingLockRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 방문자 게시글 조회 저장 서비스의 방문자 재사용과 이벤트 매핑을 검증한다.
 */
class VisitorPostViewTrackingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID POST_DETAIL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ANONYMOUS_KEY = "123e4567-e89b-12d3-a456-426614174000";

    private final VisitorRepository visitors = mock(VisitorRepository.class);
    private final VisitorEventRepository events = mock(VisitorEventRepository.class);
    private final VisitorDailySummaryRepository dailySummaries = mock(VisitorDailySummaryRepository.class);
    private final VisitorTrackingLockRepository trackingLocks = mock(VisitorTrackingLockRepository.class);
    private final VisitorPostViewTrackingService service = new VisitorPostViewTrackingService(
            visitors,
            events,
            dailySummaries,
            trackingLocks,
            CLOCK
    );

    /**
     * 신규 방문자의 방문자 행과 요청 메타데이터가 담긴 조회 이벤트 저장을 검증한다.
     */
    @Test
    void createsNewVisitorAndPostViewEventWithRequestMetadata() {
        when(visitors.findByAnonymousKey(ANONYMOUS_KEY)).thenReturn(Optional.empty());
        when(visitors.save(any(Visitor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.saveAndFlush(any(VisitorEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VisitorRequestMetadata metadata = new VisitorRequestMetadata(
                "203.0.113.10",
                "example.com",
                "JUnit Browser",
                "request-123"
        );

        service.recordPostView(ANONYMOUS_KEY, POST_DETAIL_ID, metadata);

        verify(trackingLocks).lockVisitor(ANONYMOUS_KEY);
        verify(trackingLocks).lockDailySummary(NOW.atZone(ZoneOffset.UTC).toLocalDate(), POST_DETAIL_ID);
        verify(visitors).save(any(Visitor.class));
        verify(events).saveAndFlush(org.mockito.ArgumentMatchers.argThat(event ->
                event.getVisitorId() != null
                        && POST_DETAIL_ID.equals(event.getPostDetailId())
                        && "POST_VIEW".equals(event.getEventType())
                        && NOW.equals(event.getOccurredAt())
                        && "203.0.113.0".equals(event.getIpAddress())
                        && "example.com".equals(event.getReferrerHost())
                        && "JUnit Browser".equals(event.getUserAgent())
                        && "request-123".equals(event.getRequestId())
        ));
        verify(dailySummaries).upsertPostViewSummary(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq(NOW.atZone(ZoneOffset.UTC).toLocalDate()),
                org.mockito.ArgumentMatchers.eq(POST_DETAIL_ID),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
    }

    /**
     * 기존 방문자를 재사용하고 마지막 방문 시각만 갱신하는지 검증한다.
     */
    @Test
    void reusesExistingVisitorAndUpdatesLastSeenAt() {
        Visitor existing = Visitor.create(ANONYMOUS_KEY, Instant.parse("2026-08-31T00:00:00Z"));
        when(visitors.findByAnonymousKey(ANONYMOUS_KEY)).thenReturn(Optional.of(existing));
        when(events.saveAndFlush(any(VisitorEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordPostView(ANONYMOUS_KEY, POST_DETAIL_ID, VisitorRequestMetadata.empty());

        assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        verify(trackingLocks).lockVisitor(ANONYMOUS_KEY);
        verify(trackingLocks).lockDailySummary(NOW.atZone(ZoneOffset.UTC).toLocalDate(), POST_DETAIL_ID);
        verify(visitors, never()).save(any(Visitor.class));
        verify(events).saveAndFlush(org.mockito.ArgumentMatchers.argThat(event -> existing.getId().equals(event.getVisitorId())));
        verify(dailySummaries).upsertPostViewSummary(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq(NOW.atZone(ZoneOffset.UTC).toLocalDate()),
                org.mockito.ArgumentMatchers.eq(POST_DETAIL_ID),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
    }
}
