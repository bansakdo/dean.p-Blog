package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.Visitor;
import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import com.deanp.blog.visitor.service.VisitorRequestMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL native upsert 기반 방문자 일별 집계 저장소 동작을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Testcontainers
class VisitorDailySummaryRepositoryTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEPTEMBER_SECOND = LocalDate.of(2026, 9, 2);
    private static final UUID POST_DETAIL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_POST_DETAIL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final VisitorRepository visitors;
    private final VisitorEventRepository events;
    private final VisitorDailySummaryRepository summaries;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 테스트에 필요한 방문자 저장소와 집계 저장소를 주입한다.
     *
     * @param visitors 방문자 저장소
     * @param events 방문 이벤트 저장소
     * @param summaries 일별 방문 집계 저장소
     * @param jdbcTemplate 검증용 SQL 실행기
     */
    @Autowired
    VisitorDailySummaryRepositoryTest(
            VisitorRepository visitors,
            VisitorEventRepository events,
            VisitorDailySummaryRepository summaries,
            JdbcTemplate jdbcTemplate
    ) {
        this.visitors = visitors;
        this.events = events;
        this.summaries = summaries;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Testcontainers PostgreSQL 접속 정보를 dev 프로필 데이터소스로 연결한다.
     *
     * @param registry 동적 Spring 설정 저장소
     */
    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRESQL::getJdbcUrl);
        registry.add("DB_USER", POSTGRESQL::getUsername);
        registry.add("DB_PASSWORD", POSTGRESQL::getPassword);
        registry.add("WAS_PORT", () -> "0");
    }

    /**
     * 신규 행, 반복 방문자, 다른 방문자, 다른 날짜와 게시글 분리 집계를 검증한다.
     */
    @Test
    @DisplayName("게시글 상세 조회를 날짜와 글 단위로 집계하고 고유 방문자는 중복 증가시키지 않는다")
    void upsertsPostViewSummaryByDateAndPostWithoutDuplicateUniqueVisitors() {
        insertPostFixtures();
        Visitor firstVisitor = visitors.save(Visitor.create("first-visitor", Instant.parse("2026-09-01T00:00:00Z")));
        Visitor secondVisitor = visitors.save(Visitor.create("second-visitor", Instant.parse("2026-09-01T00:00:00Z")));

        recordAndSummarize(firstVisitor, POST_DETAIL_ID, Instant.parse("2026-09-01T00:15:00Z"));
        assertSummary(POST_DETAIL_ID, SEPTEMBER_FIRST, 1L, 1L, 1L);

        recordAndSummarize(firstVisitor, POST_DETAIL_ID, Instant.parse("2026-09-01T01:15:00Z"));
        assertSummary(POST_DETAIL_ID, SEPTEMBER_FIRST, 2L, 2L, 1L);

        recordAndSummarize(secondVisitor, POST_DETAIL_ID, Instant.parse("2026-09-01T02:15:00Z"));
        assertSummary(POST_DETAIL_ID, SEPTEMBER_FIRST, 3L, 3L, 2L);

        recordAndSummarize(firstVisitor, POST_DETAIL_ID, Instant.parse("2026-09-02T00:15:00Z"));
        assertSummary(POST_DETAIL_ID, SEPTEMBER_SECOND, 1L, 1L, 1L);

        recordAndSummarize(firstVisitor, OTHER_POST_DETAIL_ID, Instant.parse("2026-09-01T03:15:00Z"));
        assertSummary(OTHER_POST_DETAIL_ID, SEPTEMBER_FIRST, 1L, 1L, 1L);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM blog.visitor_event", Long.class)).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM blog.visitor_daily_summary", Long.class)).isEqualTo(3L);
    }

    /**
     * 요약 집계 대상 게시글 상세 행을 생성한다.
     */
    private void insertPostFixtures() {
        jdbcTemplate.update("""
                INSERT INTO blog.app_user (id, login_id, password_hash, name)
                VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'summary-author', 'hash', 'Author')
                """);
        jdbcTemplate.update("""
                INSERT INTO blog.post (id, post_code, name)
                VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'summary-blog', 'Summary Blog')
                """);
        jdbcTemplate.update("""
                INSERT INTO blog.post_detail (id, post_id, author_id, slug, title, content, status, published_at)
                VALUES
                    (?, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'summary-post', 'Summary Post', 'Body', 'PUBLISHED', now()),
                    (?, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'other-summary-post', 'Other Summary Post', 'Body', 'PUBLISHED', now())
                """, POST_DETAIL_ID, OTHER_POST_DETAIL_ID);
    }

    /**
     * 원본 조회 이벤트를 저장하고 같은 날짜·게시글 요약 upsert를 실행한다.
     *
     * @param visitor 조회한 방문자
     * @param postDetailId 조회된 게시글 상세 식별자
     * @param occurredAt 조회 발생 시각
     */
    private void recordAndSummarize(Visitor visitor, UUID postDetailId, Instant occurredAt) {
        events.saveAndFlush(VisitorEvent.postView(visitor.getId(), postDetailId, occurredAt, VisitorRequestMetadata.empty()));
        summaries.upsertPostViewSummary(
                UUID.randomUUID(),
                occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
                postDetailId,
                occurredAt
        );
    }

    /**
     * 저장된 일별 요약 행의 카운터 값을 검증한다.
     *
     * @param postDetailId 검증할 게시글 상세 식별자
     * @param summaryDate 검증할 집계 날짜
     * @param landingCount 기대 상세 진입 수
     * @param viewCount 기대 조회수
     * @param uniqueVisitorCount 기대 고유 방문자 수
     */
    private void assertSummary(
            UUID postDetailId,
            LocalDate summaryDate,
            long landingCount,
            long viewCount,
            long uniqueVisitorCount
    ) {
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT landing_count, view_count, unique_visitor_count
                FROM blog.visitor_daily_summary
                WHERE post_detail_id = ?
                  AND summary_date = ?
                """, postDetailId, summaryDate);

        assertThat(summary)
                .containsEntry("landing_count", landingCount)
                .containsEntry("view_count", viewCount)
                .containsEntry("unique_visitor_count", uniqueVisitorCount);
    }
}
