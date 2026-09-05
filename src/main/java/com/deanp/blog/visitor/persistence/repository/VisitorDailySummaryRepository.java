package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.VisitorDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 방문 일별 집계 엔티티의 기본 영속성 작업을 제공한다.
 */
public interface VisitorDailySummaryRepository extends JpaRepository<VisitorDailySummary, UUID> {

    /**
     * 게시글 상세 조회 이벤트를 날짜와 게시글 단위 요약 행에 원자적으로 반영한다.
     *
     * @param summaryId 새 요약 행이 필요할 때 사용할 식별자
     * @param summaryDate 집계 기준 UTC 날짜
     * @param postDetailId 조회된 게시글 상세 식별자
     * @param occurredAt 새 요약 행의 생성 시각
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            INSERT INTO blog.visitor_daily_summary (
                id,
                summary_date,
                post_detail_id,
                landing_count,
                view_count,
                unique_visitor_count,
                created_at
            )
            VALUES (
                :summaryId,
                :summaryDate,
                :postDetailId,
                1,
                1,
                (
                    SELECT count(DISTINCT event.visitor_id)
                    FROM blog.visitor_event event
                    WHERE event.event_type = 'POST_VIEW'
                      AND event.post_detail_id = :postDetailId
                      AND event.visitor_id IS NOT NULL
                      AND event.occurred_at >= (CAST(:summaryDate AS date) AT TIME ZONE 'UTC')
                      AND event.occurred_at < ((CAST(:summaryDate AS date) + 1) AT TIME ZONE 'UTC')
                ),
                :occurredAt
            )
            ON CONFLICT (summary_date, post_detail_id)
            WHERE post_detail_id IS NOT NULL
            DO UPDATE SET
                landing_count = visitor_daily_summary.landing_count + 1,
                view_count = visitor_daily_summary.view_count + 1,
                unique_visitor_count = (
                    SELECT count(DISTINCT event.visitor_id)
                    FROM blog.visitor_event event
                    WHERE event.event_type = 'POST_VIEW'
                      AND event.post_detail_id = :postDetailId
                      AND event.visitor_id IS NOT NULL
                      AND event.occurred_at >= (CAST(:summaryDate AS date) AT TIME ZONE 'UTC')
                      AND event.occurred_at < ((CAST(:summaryDate AS date) + 1) AT TIME ZONE 'UTC')
                )
            """, nativeQuery = true)
    void upsertPostViewSummary(
            @Param("summaryId") UUID summaryId,
            @Param("summaryDate") LocalDate summaryDate,
            @Param("postDetailId") UUID postDetailId,
            @Param("occurredAt") Instant occurredAt
    );
}
