package com.deanp.blog.visitor.persistence.repository;

import com.deanp.blog.visitor.persistence.entity.VisitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 방문 이벤트 엔티티의 기본 영속성 작업과 관리 대시보드의 조회 통계를 제공한다.
 * 통계는 일별 요약(UTC 날짜) 대신 원본 이벤트를 화면 시간대 날짜로 묶어 센다.
 */
public interface VisitorEventRepository extends JpaRepository<VisitorEvent, UUID> {

    /**
     * 오늘·최근 7일·조회 기간의 글 조회수와 고유 방문자 수를 센다. 조회 기간은 오늘과 겹치지 않을 수도 있다.
     *
     * @param today 오늘 0시
     * @param week 최근 7일의 시작 시각
     * @param since 조회 기간의 시작 시각
     * @param until 조회 기간의 끝 시각(제외)
     * @param now 내일 0시, 시계가 어긋난 미래 기록을 빼기 위해 둔다
     * @return 오늘·7일·조회 기간의 조회수와 방문자 수
     */
    @Query(value = """
            SELECT count(*) FILTER (WHERE occurred_at >= :today) AS "todayViews",
                   count(DISTINCT visitor_id) FILTER (WHERE occurred_at >= :today) AS "todayVisitors",
                   count(*) FILTER (WHERE occurred_at >= :week) AS "weekViews",
                   count(DISTINCT visitor_id) FILTER (WHERE occurred_at >= :week) AS "weekVisitors",
                   count(*) FILTER (WHERE occurred_at >= :since AND occurred_at < :until) AS "periodViews",
                   count(DISTINCT visitor_id) FILTER (WHERE occurred_at >= :since AND occurred_at < :until) AS "periodVisitors"
            FROM blog.visitor_event
            WHERE event_type = 'POST_VIEW' AND occurred_at >= LEAST(CAST(:week AS timestamptz), CAST(:since AS timestamptz)) AND occurred_at < :now
            """, nativeQuery = true)
    ViewTotals countViews(@Param("today") Instant today, @Param("week") Instant week, @Param("since") Instant since,
                          @Param("until") Instant until, @Param("now") Instant now);

    /**
     * 날짜 단위(하루 또는 월요일에 시작하는 한 주)별 글 조회수와 고유 방문자 수를 센다. 조회가 없는 단위는 행이 없다.
     *
     * @param since 시작 시각
     * @param until 끝 시각(제외)
     * @param zone 날짜를 나눌 시간대 이름
     * @param unit 묶는 단위, {@code day} 또는 {@code week}
     * @return 단위 시작 날짜(yyyy-MM-dd)순 조회수와 방문자 수
     */
    @Query(value = """
            SELECT to_char(date_trunc(:unit, occurred_at AT TIME ZONE :zone), 'YYYY-MM-DD') AS "day",
                   count(*) AS "views",
                   count(DISTINCT visitor_id) AS "visitors"
            FROM blog.visitor_event
            WHERE event_type = 'POST_VIEW' AND occurred_at >= :since AND occurred_at < :until
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DailyViews> countViewsByUnit(@Param("since") Instant since, @Param("until") Instant until,
                                      @Param("zone") String zone, @Param("unit") String unit);

    /**
     * 기간 안에 많이 본 글을 고른다. 삭제한 글은 뺀다.
     *
     * @param since 시작 시각
     * @param until 끝 시각(제외)
     * @param limit 최대 개수
     * @return 조회수가 많은 순서의 글
     */
    @Query(value = """
            SELECT p.id AS "postId", p.title AS "title", p.slug AS "slug",
                   count(*) AS "views", count(DISTINCT e.visitor_id) AS "visitors"
            FROM blog.visitor_event e
            JOIN blog.post_detail p ON p.id = e.post_detail_id
            WHERE e.event_type = 'POST_VIEW' AND e.occurred_at >= :since AND e.occurred_at < :until
              AND p.status <> 'DELETED'
            GROUP BY p.id, p.title, p.slug
            ORDER BY "views" DESC, p.title
            LIMIT :limit
            """, nativeQuery = true)
    List<PostViews> findTopPosts(@Param("since") Instant since, @Param("until") Instant until, @Param("limit") int limit);

    /**
     * 기간 안의 글 조회를 어디서 들어왔는지(Referer 호스트) 묶어 센다. 호스트가 없으면 null로 묶는다.
     *
     * @param since 시작 시각
     * @param until 끝 시각(제외)
     * @param limit 최대 개수
     * @return 조회수가 많은 순서의 유입 호스트
     */
    @Query(value = """
            SELECT lower(referrer_host) AS "host", count(*) AS "views"
            FROM blog.visitor_event
            WHERE event_type = 'POST_VIEW' AND occurred_at >= :since AND occurred_at < :until
            GROUP BY 1
            ORDER BY "views" DESC, 1 NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<ReferrerViews> findTopReferrers(@Param("since") Instant since, @Param("until") Instant until,
                                         @Param("limit") int limit);

    /**
     * 기간 안의 글 조회를 접속 국가별로 센다. 국가를 모르면 null로 묶는다.
     *
     * @param since 시작 시각
     * @param until 끝 시각(제외)
     * @param limit 최대 개수
     * @return 조회수가 많은 순서의 국가 코드
     */
    @Query(value = """
            SELECT country_code AS "name", count(*) AS "views"
            FROM blog.visitor_event
            WHERE event_type = 'POST_VIEW' AND occurred_at >= :since AND occurred_at < :until
            GROUP BY 1
            ORDER BY "views" DESC, 1 NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<NamedViews> findTopCountries(@Param("since") Instant since, @Param("until") Instant until,
                                      @Param("limit") int limit);

    /**
     * 기간 안의 글 조회 가운데 검색 결과에서 들어온 조회를 검색 엔진별로 센다.
     *
     * @param since 시작 시각
     * @param until 끝 시각(제외)
     * @param limit 최대 개수
     * @return 조회수가 많은 순서의 검색 엔진 이름
     */
    @Query(value = """
            SELECT search_engine AS "name", count(*) AS "views"
            FROM blog.visitor_event
            WHERE event_type = 'POST_VIEW' AND occurred_at >= :since AND occurred_at < :until
              AND search_engine IS NOT NULL
            GROUP BY 1
            ORDER BY "views" DESC, 1
            LIMIT :limit
            """, nativeQuery = true)
    List<NamedViews> findTopSearchEngines(@Param("since") Instant since, @Param("until") Instant until,
                                          @Param("limit") int limit);

    /** 기간별 조회수와 방문자 수다. */
    interface ViewTotals {

        /** @return 오늘 조회수 */
        long getTodayViews();

        /** @return 오늘 방문자 수 */
        long getTodayVisitors();

        /** @return 최근 7일 조회수 */
        long getWeekViews();

        /** @return 최근 7일 방문자 수 */
        long getWeekVisitors();

        /** @return 조회 기간 조회수 */
        long getPeriodViews();

        /** @return 조회 기간 방문자 수 */
        long getPeriodVisitors();
    }

    /** 날짜 단위 하나의 조회수와 방문자 수다. */
    interface DailyViews {

        /** @return 단위 시작 날짜(yyyy-MM-dd) */
        String getDay();

        /** @return 조회수 */
        long getViews();

        /** @return 방문자 수 */
        long getVisitors();
    }

    /** 글 하나의 조회수와 방문자 수다. */
    interface PostViews {

        /** @return 게시글 식별자 */
        UUID getPostId();

        /** @return 제목 */
        String getTitle();

        /** @return URL 식별자 */
        String getSlug();

        /** @return 조회수 */
        long getViews();

        /** @return 방문자 수 */
        long getVisitors();
    }

    /** 국가나 검색 엔진 하나의 조회수다. */
    interface NamedViews {

        /** @return 국가 코드나 검색 엔진 이름, 국가를 모르면 null */
        String getName();

        /** @return 조회수 */
        long getViews();
    }

    /** 유입 호스트 하나의 조회수다. */
    interface ReferrerViews {

        /** @return 소문자 Referer 호스트, 없으면 null */
        String getHost();

        /** @return 조회수 */
        long getViews();
    }
}
