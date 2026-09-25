package com.deanp.blog.visitor.service;

import com.deanp.blog.persistence.TestPostgresql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL advisory lock 기반 방문자 집계의 실제 동시성 동작을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("dev")
class VisitorTrackingConcurrencyTest {

    private static final TestPostgresql POSTGRESQL = new TestPostgresql();

    private UUID authorId;
    private UUID postDetailId;

    @Autowired
    private VisitorPostViewTrackingService trackingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Testcontainers PostgreSQL 접속 정보를 dev 프로필 데이터소스로 연결한다.
     *
     * @param registry 동적 Spring 설정 저장소
     */
    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        POSTGRESQL.register(registry);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        POSTGRESQL.stop();
    }

    /**
     * 테스트마다 서로 독립된 게시글을 준비한다.
     */
    @BeforeEach
    void insertPostFixture() {
        authorId = UUID.randomUUID();
        postDetailId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO blog.app_user (id, login_id, password_hash, name)
                VALUES (?, ?, 'hash', 'Concurrency Author')
                """, authorId, "concurrency-author-" + authorId);
        jdbcTemplate.update("""
                INSERT INTO blog.post_detail (id, author_id, slug, title, content, status, published_at)
                VALUES (?, ?, ?, 'Concurrency Post', 'Body', 'PUBLISHED', now())
                """, postDetailId, authorId, "concurrency-" + postDetailId);
    }

    /** 이 테스트가 만든 게시글·이벤트·방문자만 제거한다. */
    @AfterEach
    void removePostFixture() {
        var visitorIds = jdbcTemplate.queryForList(
                "SELECT visitor_id FROM blog.visitor_event WHERE post_detail_id = ?", UUID.class, postDetailId);
        jdbcTemplate.update("DELETE FROM blog.visitor_event WHERE post_detail_id = ?", postDetailId);
        jdbcTemplate.update("DELETE FROM blog.visitor_daily_summary WHERE post_detail_id = ?", postDetailId);
        for (UUID visitorId : visitorIds) {
            jdbcTemplate.update("DELETE FROM blog.visitor WHERE id = ?", visitorId);
        }
        jdbcTemplate.update("DELETE FROM blog.post_detail WHERE id = ?", postDetailId);
        jdbcTemplate.update("DELETE FROM blog.app_user WHERE id = ?", authorId);
    }

    /**
     * 서로 다른 방문자의 동시 조회가 조회수와 고유 방문자 수를 모두 보존하는지 검증한다.
     *
     * @throws Exception 동시 작업 대기 중 오류가 발생한 경우
     */
    @Test
    @DisplayName("서로 다른 방문자의 동시 조회에서 unique visitor count를 보존한다")
    void countsDifferentVisitorsConcurrently() throws Exception {
        runConcurrently(
                "visitor-a-" + UUID.randomUUID(),
                "visitor-b-" + UUID.randomUUID()
        );

        assertCounts(2L, 2L, 2L, 2L);
    }

    /**
     * 동일 방문자의 동시 조회가 방문 이벤트 수는 늘리되 고유 방문자 수는 중복시키지 않는지 검증한다.
     *
     * @throws Exception 동시 작업 대기 중 오류가 발생한 경우
     */
    @Test
    @DisplayName("동일 방문자의 동시 조회에서 unique visitor count를 중복시키지 않는다")
    void countsSameVisitorConcurrentlyWithoutDuplicateUniqueVisitor() throws Exception {
        String anonymousKey = "same-visitor-" + UUID.randomUUID();
        runConcurrently(anonymousKey, anonymousKey);

        assertCounts(2L, 2L, 1L, 1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM blog.visitor
                WHERE anonymous_key = ?
                """, Long.class, anonymousKey)).isEqualTo(1L);
    }

    private void runConcurrently(String firstAnonymousKey, String secondAnonymousKey) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> recordAfterStart(start, firstAnonymousKey));
            Future<?> second = executor.submit(() -> recordAfterStart(start, secondAnonymousKey));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void recordAfterStart(CountDownLatch start, String anonymousKey) {
        try {
            assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
            trackingService.recordPostView(anonymousKey, postDetailId, VisitorRequestMetadata.empty());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent tracking test interrupted", exception);
        }
    }

    private void assertCounts(
            long expectedEventCount,
            long expectedViewCount,
            long expectedEventUniqueVisitorCount,
            long expectedSummaryUniqueVisitorCount
    ) {
        Map<String, Object> events = jdbcTemplate.queryForMap("""
                SELECT count(*) AS event_count,
                       count(DISTINCT visitor_id) AS event_unique_visitor_count
                FROM blog.visitor_event
                WHERE post_detail_id = ?
                  AND event_type = 'POST_VIEW'
                """, postDetailId);
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT landing_count AS landing_count,
                       view_count AS view_count,
                       unique_visitor_count AS unique_visitor_count
                FROM blog.visitor_daily_summary
                WHERE post_detail_id = ?
                """, postDetailId);

        assertThat(events)
                .containsEntry("event_count", expectedEventCount)
                .containsEntry("event_unique_visitor_count", expectedEventUniqueVisitorCount);
        assertThat(summary)
                .containsEntry("landing_count", expectedViewCount)
                .containsEntry("view_count", expectedViewCount)
                .containsEntry("unique_visitor_count", expectedSummaryUniqueVisitorCount);
    }
}
