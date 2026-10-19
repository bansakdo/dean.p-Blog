package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.persistence.TestPostgresql;
import com.deanp.blog.visitor.persistence.entity.Visitor;
import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.DailyViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.NamedViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.PostViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.ReferrerViews;
import com.deanp.blog.visitor.service.VisitorRequestMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 관리 대시보드의 조회 통계 쿼리가 한국 시간 날짜로 나누고, 방문자를 중복 없이 세고, 유입 호스트를 묶는지 검증한다.
 * CI처럼 여러 테스트가 DB를 같이 써도 섞이지 않도록 2001년 3월의 닫힌 구간만 센다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class VisitorEventRepositoryTest {

    private static final TestPostgresql POSTGRESQL = new TestPostgresql();
    private static final UUID POST = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_POST = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DELETED_POST = UUID.fromString("55555555-5555-5555-5555-555555555555");
    /** 한국 시간 2001-03-02 0시 */
    private static final Instant MARCH_SECOND = Instant.parse("2001-03-01T15:00:00Z");

    @Autowired
    private VisitorRepository visitors;
    @Autowired
    private VisitorEventRepository events;
    @Autowired
    private JdbcTemplate jdbc;

    /** @param registry 테스트 전용 데이터베이스 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRESQL.register(registry);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        POSTGRESQL.stop();
    }

    /** 기간 합계·일별 추이·많이 본 글·유입 경로를 한 번에 확인한다. */
    @Test
    void countsViewsByKoreanDayVisitorPostAndReferrer() {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, 'stats-author', 'hash', '작성자')",
                new UUID(0, 1));
        jdbc.update("""
                INSERT INTO blog.post_detail (id, author_id, slug, title, content, status, published_at)
                VALUES (?, ?, 'stats-post', '많이 본 글', '본문', 'PUBLISHED', now()),
                       (?, ?, 'stats-other', '덜 본 글', '본문', 'PUBLISHED', now()),
                       (?, ?, 'stats-deleted', '지운 글', '본문', 'DELETED', now())
                """, POST, new UUID(0, 1), OTHER_POST, new UUID(0, 1), DELETED_POST, new UUID(0, 1));
        Visitor first = visitors.save(Visitor.create("stats-first", MARCH_SECOND));
        Visitor second = visitors.save(Visitor.create("stats-second", MARCH_SECOND));

        // UTC로는 3월 1일이지만 한국 시간으로는 3월 2일 0시 30분이다.
        view(first, POST, MARCH_SECOND.plusSeconds(1800), "https://www.Google.com/search", "KR");
        view(first, POST, MARCH_SECOND.plusSeconds(3600), "https://blog.example/posts/a", "KR");
        view(second, POST, MARCH_SECOND.plusSeconds(7200), null, "US");
        // 한국 시간 3월 1일 23시 59분은 전날로 센다.
        view(second, OTHER_POST, MARCH_SECOND.minusSeconds(60), "https://search.naver.com/search.naver", null);
        // 한국 시간 2월 25일: 30일 합계에만 들고, 삭제한 글이라 많이 본 글에서는 빠진다.
        Visitor third = visitors.save(Visitor.create("stats-third", MARCH_SECOND));
        view(third, DELETED_POST, MARCH_SECOND.minusSeconds(5 * 86_400), null, "KR");
        // 끝 시각(3월 3일 0시)부터는 세지 않는다.
        view(third, POST, MARCH_SECOND.plusSeconds(86_400), "https://www.google.com/", "JP");

        Instant week = MARCH_SECOND.minusSeconds(86_400);
        Instant since = MARCH_SECOND.minusSeconds(10 * 86_400);
        Instant until = MARCH_SECOND.plusSeconds(86_400);
        var totals = events.countViews(MARCH_SECOND, week, since, until);
        assertThat(totals.getTodayViews()).isEqualTo(3);
        assertThat(totals.getTodayVisitors()).isEqualTo(2);
        assertThat(totals.getWeekViews()).isEqualTo(4);
        assertThat(totals.getWeekVisitors()).isEqualTo(2);
        assertThat(totals.getMonthViews()).isEqualTo(5);
        assertThat(totals.getMonthVisitors()).isEqualTo(3);

        assertThat(events.countDailyViews(since, until, "Asia/Seoul"))
                .extracting(DailyViews::getDay, DailyViews::getViews, DailyViews::getVisitors)
                .containsExactly(tuple("2001-02-25", 1L, 1L), tuple("2001-03-01", 1L, 1L), tuple("2001-03-02", 3L, 2L));
        assertThat(events.findTopPosts(since, until, 10))
                .extracting(PostViews::getTitle, PostViews::getSlug, PostViews::getViews, PostViews::getVisitors)
                .containsExactly(tuple("많이 본 글", "stats-post", 3L, 2L), tuple("덜 본 글", "stats-other", 1L, 1L));
        assertThat(events.findTopReferrers(since, until, 10))
                .extracting(ReferrerViews::getHost, ReferrerViews::getViews)
                .containsExactly(tuple(null, 2L), tuple("blog.example", 1L), tuple("search.naver.com", 1L),
                        tuple("www.google.com", 1L));
        assertThat(events.findTopCountries(since, until, 10))
                .extracting(NamedViews::getName, NamedViews::getViews)
                .containsExactly(tuple("KR", 3L), tuple("US", 1L), tuple(null, 1L));
        assertThat(events.findTopSearchEngines(since, until, 10))
                .extracting(NamedViews::getName, NamedViews::getViews)
                .containsExactly(tuple("Google", 1L), tuple("Naver", 1L));
        assertThat(events.findTopPosts(since, until, 1)).hasSize(1);
    }

    /**
     * @param visitor 방문자
     * @param postId 본 글
     * @param occurredAt 본 시각
     * @param referrer Referer 헤더 값 또는 null
     * @param country 국가 헤더 값 또는 null
     */
    private void view(Visitor visitor, UUID postId, Instant occurredAt, String referrer, String country) {
        events.saveAndFlush(VisitorEvent.postView(visitor.getId(), postId, occurredAt,
                new VisitorRequestMetadata(null, referrer, null, null, country)));
    }
}
