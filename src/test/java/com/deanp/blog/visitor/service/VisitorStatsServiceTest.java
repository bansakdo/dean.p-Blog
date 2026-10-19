package com.deanp.blog.visitor.service;

import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.DailyViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.NamedViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.ReferrerViews;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.ViewTotals;
import com.deanp.blog.visitor.service.VisitorStatsService.ChartPoint;
import com.deanp.blog.visitor.service.VisitorStatsService.NamedCount;
import com.deanp.blog.visitor.service.VisitorStatsService.StatsPeriod;
import com.deanp.blog.visitor.service.VisitorStatsService.StatsRange;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 대시보드 통계가 한국 시간 기준 기간을 쓰고, 빈 날·주를 0으로 채우고, 유입 경로 이름을 붙이고, 고른 기간을 해석하는지 검증한다. */
class VisitorStatsServiceTest {

    /** UTC 10월 18일 16시는 한국 시간 10월 19일 1시다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"), ZoneOffset.UTC);
    private static final Instant TODAY = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant WEEK = Instant.parse("2026-10-12T15:00:00Z");
    private static final Instant MONTH = Instant.parse("2026-09-19T15:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-10-19T15:00:00Z");
    private static final LocalDate TODAY_DATE = LocalDate.of(2026, 10, 19);

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
        when(events.countViews(TODAY, WEEK, MONTH, UNTIL, UNTIL)).thenReturn(totals);
        when(events.countViewsByUnit(MONTH, UNTIL, "Asia/Seoul", "day")).thenReturn(List.of(quiet, busy));
        when(events.findTopReferrers(eq(MONTH), eq(UNTIL), anyInt())).thenReturn(List.of(own, direct, google, ownWww));
        NamedViews korea = named("KR", 6);
        NamedViews unknown = named(null, 2);
        NamedViews naver = named("Naver", 4);
        when(events.findTopCountries(eq(MONTH), eq(UNTIL), anyInt())).thenReturn(List.of(korea, unknown));
        when(events.findTopSearchEngines(eq(MONTH), eq(UNTIL), anyInt())).thenReturn(List.of(naver));

        var stats = service.summarize("Blog.Example", service.resolve(null, null, null));

        verify(events).countViews(TODAY, WEEK, MONTH, UNTIL, UNTIL);
        assertThat(stats.range().label()).isEqualTo("최근 30일");
        assertThat(stats.today().views()).isEqualTo(8);
        assertThat(stats.period().visitors()).isEqualTo(5);
        assertThat(stats.chart()).hasSize(30);
        assertThat(stats.chart().getFirst()).isEqualTo(new ChartPoint(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), 0, 0));
        assertThat(stats.chart().getLast()).isEqualTo(new ChartPoint(TODAY_DATE, TODAY_DATE, 8, 3));
        assertThat(stats.chart().getLast().label()).isEqualTo("10월 19일");
        assertThat(stats.chart()).filteredOn(day -> day.views() > 0).hasSize(2);
        assertThat(stats.peak()).isEqualTo(8);
        assertThat(stats.barHeight(8)).isEqualTo(100);
        assertThat(stats.barHeight(0)).isZero();
        assertThat(stats.barHeight(2)).isEqualTo(25);
        assertThat(stats.visitorLine()).startsWith("0.5,100.0 1.5,100.0").endsWith(" 29.5,62.5");
        assertThat(stats.referrers()).containsExactly(
                new NamedCount("블로그 안 이동", 5), new NamedCount("직접 방문·알 수 없음", 4), new NamedCount("www.google.com", 3),
                new NamedCount("블로그 안 이동", 1));
        assertThat(stats.topPosts()).isEmpty();
        assertThat(stats.countries()).containsExactly(new NamedCount("대한민국 (KR)", 6), new NamedCount("알 수 없음", 2));
        assertThat(stats.searchEngines()).containsExactly(new NamedCount("Naver", 4));
    }

    /** 기간 버튼은 오늘로 끝나고, 시작일·종료일이 둘 다 맞으면 그 기간을 쓰며, 잘못된 값은 최근 30일과 안내 문구로 바꾼다. */
    @Test
    void resolvesPresetsCustomRangesAndInvalidInput() {
        assertThat(service.today()).isEqualTo(TODAY_DATE);
        assertThat(service.resolve("7d", null, null)).isEqualTo(
                new StatsRange(LocalDate.of(2026, 10, 13), TODAY_DATE, StatsPeriod.DAYS_7, null));
        assertThat(service.resolve("90d", null, null).from()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(service.resolve("6m", null, null).from()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(service.resolve("1y", null, null).from()).isEqualTo(LocalDate.of(2025, 10, 20));
        assertThat(service.resolve("90d", null, null).weekly()).isFalse();
        assertThat(service.resolve("6m", null, null).weekly()).isTrue();

        StatsRange custom = service.resolve("7d", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(custom).isEqualTo(new StatsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null));
        assertThat(custom.label()).isEqualTo("2026.9.1~2026.9.30");
        assertThat(service.resolve(null, LocalDate.of(2025, 10, 19), TODAY_DATE).error()).isNull();

        assertThat(service.resolve("2w", null, null).preset()).isEqualTo(StatsPeriod.DAYS_30);
        assertThat(service.resolve("2w", null, null).error()).isNotNull();
        assertThat(service.resolve(null, LocalDate.of(2026, 9, 1), null).error()).contains("모두");
        assertThat(service.resolve(null, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)).error()).contains("늦습니다");
        assertThat(service.resolve(null, LocalDate.of(2026, 9, 1), TODAY_DATE.plusDays(1)).error()).contains("오늘");
        StatsRange tooLong = service.resolve(null, LocalDate.of(2025, 10, 18), TODAY_DATE);
        assertThat(tooLong.error()).contains("366");
        assertThat(tooLong.preset()).isEqualTo(StatsPeriod.DAYS_30);
        assertThat(tooLong.to()).isEqualTo(TODAY_DATE);
    }

    /** 92일을 넘는 기간은 월요일에 시작하는 주로 묶고, 첫 주와 마지막 주는 기간 안 날짜만 표시한다. 오늘·7일 합계는 기간과 따로 센다. */
    @Test
    void groupsLongRangesByWeek() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 3);
        Instant since = Instant.parse("2025-12-31T15:00:00Z");
        Instant until = Instant.parse("2026-06-03T15:00:00Z");
        ViewTotals totals = totals();
        DailyViews firstWeek = daily("2025-12-29", 4, 2);
        DailyViews lastWeek = daily("2026-06-01", 6, 3);
        when(events.countViews(TODAY, WEEK, since, until, UNTIL)).thenReturn(totals);
        when(events.countViewsByUnit(since, until, "Asia/Seoul", "week")).thenReturn(List.of(firstWeek, lastWeek));
        when(events.findTopReferrers(any(), any(), anyInt())).thenReturn(List.of());

        var stats = service.summarize("blog.example", service.resolve(null, from, to));

        assertThat(stats.range().weekly()).isTrue();
        assertThat(stats.chart()).hasSize(23);
        assertThat(stats.chart().getFirst()).isEqualTo(new ChartPoint(from, LocalDate.of(2026, 1, 4), 4, 2));
        assertThat(stats.chart().get(1)).isEqualTo(new ChartPoint(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), 0, 0));
        assertThat(stats.chart().getLast()).isEqualTo(new ChartPoint(LocalDate.of(2026, 6, 1), to, 6, 3));
        assertThat(stats.chart().getLast().label()).isEqualTo("6월 1일~6월 3일");
        assertThat(stats.peak()).isEqualTo(6);
        assertThat(stats.today().views()).isEqualTo(8);
    }

    /** @return 오늘 8회·3명, 7일 20회·4명, 30일 30회·5명 */
    private static ViewTotals totals() {
        ViewTotals totals = mock(ViewTotals.class);
        when(totals.getTodayViews()).thenReturn(8L);
        when(totals.getTodayVisitors()).thenReturn(3L);
        when(totals.getWeekViews()).thenReturn(20L);
        when(totals.getWeekVisitors()).thenReturn(4L);
        when(totals.getPeriodViews()).thenReturn(30L);
        when(totals.getPeriodVisitors()).thenReturn(5L);
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

    /**
     * @param name 국가 코드나 검색 엔진 이름 또는 null
     * @param views 조회수
     * @return 이름별 합계
     */
    private static NamedViews named(String name, long views) {
        NamedViews named = mock(NamedViews.class);
        when(named.getName()).thenReturn(name);
        when(named.getViews()).thenReturn(views);
        return named;
    }

    /** @param host 호스트 또는 null @param views 조회수 @return 유입 호스트 합계 */
    private static ReferrerViews referrer(String host, long views) {
        ReferrerViews referrer = mock(ReferrerViews.class);
        when(referrer.getHost()).thenReturn(host);
        when(referrer.getViews()).thenReturn(views);
        return referrer;
    }
}
