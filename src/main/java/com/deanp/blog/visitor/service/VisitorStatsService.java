package com.deanp.blog.visitor.service;

import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository.DailyViews;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    /** 이 날 수까지는 하루씩, 넘으면 한 주씩 묶어 그래프를 그린다 */
    static final int DAILY_LIMIT = 92;
    /** 직접 고르는 조회 기간의 최대 날 수 */
    static final int MAX_DAYS = 366;
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
     * 화면에서 고른 값을 조회 기간으로 바꾼다. 시작일·종료일이 둘 다 있으면 직접 고른 기간을, 아니면 기간 버튼을 쓴다.
     * 잘못된 값이면 기본 기간(최근 30일)과 안내 문구를 돌려준다.
     *
     * @param period 기간 버튼 값 또는 null
     * @param from 직접 고른 시작일 또는 null
     * @param to 직접 고른 종료일 또는 null
     * @return 조회 기간
     */
    public StatsRange resolve(String period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(DISPLAY_ZONE));
        if (from != null || to != null) {
            String error = rangeError(from, to, today);
            return error == null ? new StatsRange(from, to, null, null) : preset(StatsPeriod.DEFAULT, today, error);
        }
        StatsPeriod preset = StatsPeriod.of(period);
        return preset(preset == null ? StatsPeriod.DEFAULT : preset, today,
                period == null || preset != null ? null : "알 수 없는 기간이라 최근 30일을 보여 줍니다.");
    }

    /**
     * 오늘·7일·조회 기간 합계, 조회 기간 추이, 많이 본 글, 유입 경로·국가·검색 유입을 모은다.
     *
     * @param siteHost 블로그 자신의 호스트, 이 호스트에서 온 조회는 블로그 안 이동으로 표시한다
     * @param range 조회 기간
     * @return 대시보드 통계
     */
    @Transactional(readOnly = true)
    public VisitorStats summarize(String siteHost, StatsRange range) {
        LocalDate today = LocalDate.now(clock.withZone(DISPLAY_ZONE));
        Instant since = startOf(range.from());
        Instant until = startOf(range.to().plusDays(1));
        var totals = events.countViews(startOf(today), startOf(today.minusDays(6)), since, until, startOf(today.plusDays(1)));

        // 조회가 없던 단위도 0으로 채워 막대가 빠지지 않게 한다. 주 단위는 월요일에 시작하고 기간 밖은 잘라 표시한다.
        boolean weekly = range.weekly();
        Map<String, DailyViews> byUnit = events.countViewsByUnit(since, until, DISPLAY_ZONE.getId(), weekly ? "week" : "day")
                .stream().collect(Collectors.toMap(DailyViews::getDay, Function.identity()));
        List<ChartPoint> chart = new ArrayList<>();
        LocalDate unit = weekly ? range.from().with(DayOfWeek.MONDAY) : range.from();
        for (; !unit.isAfter(range.to()); unit = unit.plusDays(weekly ? 7 : 1)) {
            DailyViews views = byUnit.get(unit.toString());
            LocalDate first = unit.isBefore(range.from()) ? range.from() : unit;
            LocalDate end = weekly ? unit.plusDays(6) : unit;
            LocalDate last = end.isAfter(range.to()) ? range.to() : end;
            chart.add(new ChartPoint(first, last, views == null ? 0 : views.getViews(), views == null ? 0 : views.getVisitors()));
        }
        long peak = chart.stream().mapToLong(ChartPoint::views).max().orElse(0);

        List<TopPost> posts = events.findTopPosts(since, until, TOP_LIMIT).stream()
                .map(post -> new TopPost(post.getPostId(), post.getTitle(), post.getSlug(), post.getViews(), post.getVisitors()))
                .toList();
        String own = withoutWww(siteHost == null ? "" : siteHost.toLowerCase(Locale.ROOT));
        List<NamedCount> referrers = events.findTopReferrers(since, until, TOP_LIMIT).stream()
                .map(referrer -> new NamedCount(label(referrer.getHost(), own), referrer.getViews()))
                .toList();
        List<NamedCount> countries = events.findTopCountries(since, until, TOP_LIMIT).stream()
                .map(country -> new NamedCount(countryName(country.getName()), country.getViews()))
                .toList();
        List<NamedCount> searchEngines = events.findTopSearchEngines(since, until, TOP_LIMIT).stream()
                .map(engine -> new NamedCount(engine.getName(), engine.getViews()))
                .toList();

        return new VisitorStats(range,
                new Period(totals.getTodayViews(), totals.getTodayVisitors()),
                new Period(totals.getWeekViews(), totals.getWeekVisitors()),
                new Period(totals.getPeriodViews(), totals.getPeriodVisitors()),
                chart, peak, posts, referrers, countries, searchEngines);
    }

    /**
     * @param preset 기간 버튼
     * @param today 오늘
     * @param error 안내 문구 또는 null
     * @return 오늘로 끝나는 기간 버튼의 조회 기간
     */
    private static StatsRange preset(StatsPeriod preset, LocalDate today, String error) {
        return new StatsRange(preset.firstDay(today), today, preset, error);
    }

    /**
     * @param from 시작일 또는 null
     * @param to 종료일 또는 null
     * @param today 오늘
     * @return 직접 고른 기간이 잘못됐을 때의 안내 문구, 올바르면 null
     */
    private static String rangeError(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null) {
            return "시작일과 종료일을 모두 골라 주세요. 최근 30일을 보여 줍니다.";
        }
        if (from.isAfter(to)) {
            return "시작일이 종료일보다 늦습니다. 최근 30일을 보여 줍니다.";
        }
        if (to.isAfter(today)) {
            return "종료일은 오늘 이전이어야 합니다. 최근 30일을 보여 줍니다.";
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            return "기간은 최대 " + MAX_DAYS + "일까지 고를 수 있습니다. 최근 30일을 보여 줍니다.";
        }
        return null;
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
     * @param range 조회 기간
     * @param today 오늘
     * @param week 최근 7일
     * @param period 조회 기간 합계
     * @param chart 조회 기간의 날짜순 추이, 기간이 길면 한 주씩 묶는다
     * @param peak 추이의 조회수 최댓값, 막대 높이와 선 높이의 기준
     * @param topPosts 조회 기간에 많이 본 글
     * @param referrers 조회 기간 유입 경로
     * @param countries 조회 기간 접속 국가
     * @param searchEngines 조회 기간 검색 유입
     */
    public record VisitorStats(StatsRange range, Period today, Period week, Period period, List<ChartPoint> chart, long peak,
                               List<TopPost> topPosts, List<NamedCount> referrers, List<NamedCount> countries,
                               List<NamedCount> searchEngines) {

        /**
         * @param views 조회수
         * @return 최댓값에 대한 막대 높이 백분율, 조회가 있으면 최소 2
         */
        public long barHeight(long views) {
            return peak == 0 || views == 0 ? 0 : Math.max(2, Math.round(views * 100.0 / peak));
        }

        /**
         * 방문자 수 꺾은선의 SVG 좌표다. 가로는 막대 하나를 1로, 세로는 막대와 같은 기준(조회수 최댓값)의 100을 위로 둔다.
         *
         * @return {@code polyline}의 points 값
         */
        public String visitorLine() {
            StringBuilder points = new StringBuilder();
            for (int i = 0; i < chart.size(); i++) {
                long visitors = chart.get(i).visitors();
                double y = peak == 0 ? 100 : 100 - visitors * 100.0 / peak;
                points.append(i == 0 ? "" : " ").append(i).append(".5,").append(Math.round(y * 10) / 10.0);
            }
            return points.toString();
        }
    }

    /**
     * 화면의 기간 버튼이다. 끝은 늘 오늘이다.
     */
    public enum StatsPeriod {
        /** 최근 7일 */
        DAYS_7("7d", "7일"),
        /** 최근 30일, 기본값 */
        DAYS_30("30d", "30일"),
        /** 최근 90일 */
        DAYS_90("90d", "90일"),
        /** 최근 6개월 */
        MONTHS_6("6m", "6개월"),
        /** 최근 1년 */
        YEAR_1("1y", "1년");

        /** 아무것도 고르지 않았을 때의 기간 */
        public static final StatsPeriod DEFAULT = DAYS_30;

        private final String key;
        private final String label;

        /**
         * @param key 주소에 쓰는 값
         * @param label 화면 이름
         */
        StatsPeriod(String key, String label) {
            this.key = key;
            this.label = label;
        }

        /** @return 주소에 쓰는 값 */
        public String key() {
            return key;
        }

        /** @return 화면 이름 */
        public String label() {
            return label;
        }

        /**
         * @param today 오늘
         * @return 오늘을 포함해 이 기간이 시작하는 날
         */
        LocalDate firstDay(LocalDate today) {
            return switch (this) {
                case DAYS_7 -> today.minusDays(6);
                case DAYS_30 -> today.minusDays(29);
                case DAYS_90 -> today.minusDays(89);
                case MONTHS_6 -> today.minusMonths(6).plusDays(1);
                case YEAR_1 -> today.minusYears(1).plusDays(1);
            };
        }

        /**
         * @param key 주소에 쓰는 값 또는 null
         * @return 맞는 기간 버튼, 없으면 null
         */
        static StatsPeriod of(String key) {
            for (StatsPeriod period : values()) {
                if (period.key.equals(key)) {
                    return period;
                }
            }
            return null;
        }
    }

    /**
     * 통계를 낼 날짜 범위다. 양 끝 날짜를 모두 포함한다.
     *
     * @param from 시작일
     * @param to 종료일
     * @param preset 기간 버튼으로 골랐으면 그 버튼, 직접 골랐으면 null
     * @param error 입력이 잘못돼 기본 기간으로 바꿨을 때의 안내 문구 또는 null
     */
    public record StatsRange(LocalDate from, LocalDate to, StatsPeriod preset, String error) {

        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.M.d");

        /** @return 그래프를 한 주씩 묶는지 여부 */
        public boolean weekly() {
            return ChronoUnit.DAYS.between(from, to) + 1 > DAILY_LIMIT;
        }

        /** @return 화면에 보일 기간 이름, 예: 최근 30일, 2026.9.1~2026.9.30 */
        public String label() {
            return preset != null ? "최근 " + preset.label() : DATE.format(from) + "~" + DATE.format(to);
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
     * 그래프의 막대 하나(하루 또는 한 주)의 합계다.
     *
     * @param first 첫날
     * @param last 마지막 날, 하루씩 묶으면 첫날과 같다
     * @param views 조회수
     * @param visitors 고유 방문자 수
     */
    public record ChartPoint(LocalDate first, LocalDate last, long views, long visitors) {

        private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일");

        /** @return 화면에 보일 날짜, 예: 10월 19일, 10월 13일~10월 19일 */
        public String label() {
            return first.equals(last) ? DAY.format(first) : DAY.format(first) + "~" + DAY.format(last);
        }
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
