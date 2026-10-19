package com.deanp.blog.visitor.service;

import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.DailyViews;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리 대시보드에 보여 줄 글 조회 통계를 화면 시간대(한국 시간) 날짜 기준으로 모은다.
 */
@Service
public class VisitorStatsService {

    /** 일별 추이를 보여 줄 날 수 */
    static final int DAYS = 30;
    private static final int TOP_LIMIT = 10;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final VisitorEventRepository events;
    private final Clock clock;

    /**
     * 시스템 시계를 쓰는 통계 서비스를 만든다.
     *
     * @param events 방문 이벤트 저장소
     */
    @Autowired
    public VisitorStatsService(VisitorEventRepository events) {
        this(events, Clock.systemUTC());
    }

    /**
     * 테스트에서 고정 시계를 넣을 수 있는 통계 서비스를 만든다.
     *
     * @param events 방문 이벤트 저장소
     * @param clock 오늘을 정할 시계
     */
    VisitorStatsService(VisitorEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * 오늘·7일·30일 합계, 최근 30일 일별 추이, 많이 본 글, 유입 경로를 모은다.
     *
     * @param siteHost 블로그 자신의 호스트, 이 호스트에서 온 조회는 블로그 안 이동으로 표시한다
     * @return 대시보드 통계
     */
    @Transactional(readOnly = true)
    public VisitorStats summarize(String siteHost) {
        LocalDate today = LocalDate.now(clock.withZone(DISPLAY_ZONE));
        LocalDate firstDay = today.minusDays(DAYS - 1);
        Instant month = startOf(firstDay);
        Instant until = startOf(today.plusDays(1));
        var totals = events.countViews(startOf(today), startOf(today.minusDays(6)), month, until);

        // 조회가 없던 날도 0으로 채워 막대가 빠지지 않게 한다.
        Map<String, DailyViews> byDay = events.countDailyViews(month, until, DISPLAY_ZONE.getId()).stream()
                .collect(Collectors.toMap(DailyViews::getDay, Function.identity()));
        List<DayViews> daily = new ArrayList<>(DAYS);
        for (LocalDate day = firstDay; !day.isAfter(today); day = day.plusDays(1)) {
            DailyViews views = byDay.get(day.toString());
            daily.add(new DayViews(day, views == null ? 0 : views.getViews(), views == null ? 0 : views.getVisitors()));
        }
        long peak = daily.stream().mapToLong(DayViews::views).max().orElse(0);

        List<TopPost> posts = events.findTopPosts(month, until, TOP_LIMIT).stream()
                .map(post -> new TopPost(post.getPostId(), post.getTitle(), post.getSlug(), post.getViews(), post.getVisitors()))
                .toList();
        String own = withoutWww(siteHost == null ? "" : siteHost.toLowerCase(Locale.ROOT));
        List<NamedCount> referrers = events.findTopReferrers(month, until, TOP_LIMIT).stream()
                .map(referrer -> new NamedCount(label(referrer.getHost(), own), referrer.getViews()))
                .toList();
        List<NamedCount> countries = events.findTopCountries(month, until, TOP_LIMIT).stream()
                .map(country -> new NamedCount(countryName(country.getName()), country.getViews()))
                .toList();
        List<NamedCount> searchEngines = events.findTopSearchEngines(month, until, TOP_LIMIT).stream()
                .map(engine -> new NamedCount(engine.getName(), engine.getViews()))
                .toList();

        return new VisitorStats(
                new Period(totals.getTodayViews(), totals.getTodayVisitors()),
                new Period(totals.getWeekViews(), totals.getWeekVisitors()),
                new Period(totals.getMonthViews(), totals.getMonthVisitors()),
                daily, peak, posts, referrers, countries, searchEngines);
    }

    /**
     * @param day 화면 시간대 날짜
     * @return 그날 0시
     */
    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(DISPLAY_ZONE).toInstant();
    }

    /**
     * @param host 소문자 Referer 호스트 또는 null
     * @param own 블로그 자신의 소문자 호스트(www. 없이)
     * @return 화면에 보일 유입 경로 이름
     */
    private static String label(String host, String own) {
        if (host == null) {
            return "직접 방문·알 수 없음";
        }
        return withoutWww(host).equals(own) ? "블로그 안 이동" : host;
    }

    /**
     * @param code 국가 코드 또는 null
     * @return 한국어 국가 이름, 모르면 안내 문구
     */
    private static String countryName(String code) {
        if (code == null) {
            return "알 수 없음";
        }
        String name = Locale.of("", code).getDisplayCountry(Locale.KOREAN);
        return name.isBlank() || name.equals(code) ? code : name + " (" + code + ")";
    }

    /**
     * www.가 붙은 주소와 붙지 않은 주소를 같은 사이트로 본다.
     *
     * @param host 소문자 호스트
     * @return 앞의 www.를 뗀 호스트
     */
    private static String withoutWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /**
     * 대시보드 통계다.
     *
     * @param today 오늘
     * @param week 최근 7일
     * @param month 최근 30일
     * @param daily 최근 30일 날짜순 추이
     * @param peak 일별 조회수 최댓값, 막대 높이의 기준
     * @param topPosts 최근 30일 많이 본 글
     * @param referrers 최근 30일 유입 경로
     * @param countries 최근 30일 접속 국가
     * @param searchEngines 최근 30일 검색 유입
     */
    public record VisitorStats(Period today, Period week, Period month, List<DayViews> daily, long peak,
                               List<TopPost> topPosts, List<NamedCount> referrers, List<NamedCount> countries,
                               List<NamedCount> searchEngines) {

        /**
         * @param views 그날 조회수
         * @return 최댓값에 대한 막대 높이 백분율, 조회가 있으면 최소 2
         */
        public long barHeight(long views) {
            return peak == 0 || views == 0 ? 0 : Math.max(2, Math.round(views * 100.0 / peak));
        }
    }

    /**
     * 한 기간의 합계다.
     *
     * @param views 조회수
     * @param visitors 고유 방문자 수
     */
    public record Period(long views, long visitors) {
    }

    /**
     * 하루의 합계다.
     *
     * @param day 화면 시간대 날짜
     * @param views 조회수
     * @param visitors 고유 방문자 수
     */
    public record DayViews(LocalDate day, long views, long visitors) {
    }

    /**
     * 많이 본 글 하나다.
     *
     * @param id 게시글 식별자
     * @param title 제목
     * @param slug URL 식별자
     * @param views 조회수
     * @param visitors 고유 방문자 수
     */
    public record TopPost(UUID id, String title, String slug, long views, long visitors) {
    }

    /**
     * 유입 경로·국가·검색 엔진처럼 이름과 조회수만 있는 통계 한 줄이다.
     *
     * @param label 화면에 보일 이름
     * @param views 조회수
     */
    public record NamedCount(String label, long views) {
    }
}
