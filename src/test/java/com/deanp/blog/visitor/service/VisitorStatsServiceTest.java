package com.deanp.blog.visitor.service;

import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.DailyViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.ReferrerViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.ViewTotals;
import com.deanp.blog.visitor.service.VisitorStatsService.DayViews;
import com.deanp.blog.visitor.service.VisitorStatsService.Referrer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 대시보드 통계가 한국 시간 기준 기간을 쓰고, 빈 날을 0으로 채우고, 유입 경로 이름을 붙이는지 검증한다. */
class VisitorStatsServiceTest {

    /** UTC 10월 18일 16시는 한국 시간 10월 19일 1시다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"), ZoneOffset.UTC);
    private static final Instant TODAY = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant WEEK = Instant.parse("2026-10-12T15:00:00Z");
    private static final Instant MONTH = Instant.parse("2026-09-19T15:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-10-19T15:00:00Z");

    private final VisitorEventRepository events = mock(VisitorEventRepository.class);
    private final VisitorStatsService service = new VisitorStatsService(events, CLOCK);

    /** 한국 시간 오늘 0시·7일 전·30일 전부터 내일 0시 전까지 세고, 30일 막대를 빠짐없이 만들고, www. 유무와 관계없이 블로그 안 이동을 알아본다. */
    @Test
    void summarizesByKoreanDaysAndFillsEmptyDays() {
        ViewTotals totals = totals();
        DailyViews busy = daily("2026-10-19", 8, 3);
        DailyViews quiet = daily("2026-10-01", 2, 1);
        ReferrerViews own = referrer("blog.example", 5);
        ReferrerViews ownWww = referrer("www.blog.example", 1);
        ReferrerViews direct = referrer(null, 4);
        ReferrerViews google = referrer("www.google.com", 3);
        when(events.countViews(TODAY, WEEK, MONTH, UNTIL)).thenReturn(totals);
        when(events.countDailyViews(MONTH, UNTIL, "Asia/Seoul")).thenReturn(List.of(quiet, busy));
        when(events.findTopReferrers(eq(MONTH), eq(UNTIL), anyInt())).thenReturn(List.of(own, direct, google, ownWww));

        var stats = service.summarize("Blog.Example");

        verify(events).countViews(TODAY, WEEK, MONTH, UNTIL);
        assertThat(stats.today().views()).isEqualTo(8);
        assertThat(stats.month().visitors()).isEqualTo(5);
        assertThat(stats.daily()).hasSize(VisitorStatsService.DAYS);
        assertThat(stats.daily().getFirst()).isEqualTo(new DayViews(LocalDate.of(2026, 9, 20), 0, 0));
        assertThat(stats.daily().getLast()).isEqualTo(new DayViews(LocalDate.of(2026, 10, 19), 8, 3));
        assertThat(stats.daily()).filteredOn(day -> day.views() > 0).hasSize(2);
        assertThat(stats.peak()).isEqualTo(8);
        assertThat(stats.barHeight(8)).isEqualTo(100);
        assertThat(stats.barHeight(0)).isZero();
        assertThat(stats.barHeight(2)).isEqualTo(25);
        assertThat(stats.referrers()).containsExactly(
                new Referrer("블로그 안 이동", 5), new Referrer("직접 방문·알 수 없음", 4), new Referrer("www.google.com", 3),
                new Referrer("블로그 안 이동", 1));
        assertThat(stats.topPosts()).isEmpty();
    }

    /** @return 오늘 8회·3명, 7일 20회·4명, 30일 30회·5명 */
    private static ViewTotals totals() {
        ViewTotals totals = mock(ViewTotals.class);
        when(totals.getTodayViews()).thenReturn(8L);
        when(totals.getTodayVisitors()).thenReturn(3L);
        when(totals.getWeekViews()).thenReturn(20L);
        when(totals.getWeekVisitors()).thenReturn(4L);
        when(totals.getMonthViews()).thenReturn(30L);
        when(totals.getMonthVisitors()).thenReturn(5L);
        return totals;
    }

    /** @param day 날짜 @param views 조회수 @param visitors 방문자 수 @return 하루 합계 */
    private static DailyViews daily(String day, long views, long visitors) {
        DailyViews daily = mock(DailyViews.class);
        when(daily.getDay()).thenReturn(day);
        when(daily.getViews()).thenReturn(views);
        when(daily.getVisitors()).thenReturn(visitors);
        return daily;
    }

    /** @param host 호스트 또는 null @param views 조회수 @return 유입 호스트 합계 */
    private static ReferrerViews referrer(String host, long views) {
        ReferrerViews referrer = mock(ReferrerViews.class);
        when(referrer.getHost()).thenReturn(host);
        when(referrer.getViews()).thenReturn(views);
        return referrer;
    }
}
