package com.deanp.blog.post.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.post.service.PostEditorService.AdminPostRow;
import com.deanp.blog.post.service.PostEditorService.PostSummary;
import com.deanp.blog.visitor.service.VisitorStatsService;
import com.deanp.blog.visitor.service.VisitorStatsService.ChartPoint;
import com.deanp.blog.visitor.service.VisitorStatsService.Period;
import com.deanp.blog.visitor.service.VisitorStatsService.NamedCount;
import com.deanp.blog.visitor.service.VisitorStatsService.StatsPeriod;
import com.deanp.blog.visitor.service.VisitorStatsService.StatsRange;
import com.deanp.blog.visitor.service.VisitorStatsService.TopPost;
import com.deanp.blog.visitor.service.VisitorStatsService.VisitorStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 관리 대시보드의 권한 경계와, 글 현황·조회 통계를 화면에 그리는지 검증한다. */
@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfiguration.class)
class AdminDashboardControllerTest {

    private static final UUID POST_ID = new UUID(0, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostEditorService postEditor;

    @MockitoBean
    private VisitorStatsService visitorStats;

    /** 로그인하지 않았으면 로그인 화면으로, 글 관리 권한이 없으면 거부한다. */
    @Test
    void requiresPostWrite() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin").with(user("reader")))
                .andExpect(status().isForbidden());
    }

    /** 글 수, 조회 합계, 일별 막대, 많이 본 글, 유입 경로, 예약·최근 글을 저장 금지 헤더와 함께 보여 준다. */
    @Test
    void showsPostSummaryAndViewStats() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 10, 20, 9, 0);
        AdminPostRow scheduled = new AdminPostRow(POST_ID, "next", "다음 글", true, false, true, at, at);
        AdminPostRow recent = new AdminPostRow(new UUID(0, 2), "draft", "고친 초안", false, false, false, null, at);
        when(postEditor.summarize()).thenReturn(new PostSummary(12, 1, 3, 2, List.of(scheduled), List.of(recent)));
        LocalDate yesterday = LocalDate.of(2026, 10, 18);
        LocalDate today = LocalDate.of(2026, 10, 19);
        List<ChartPoint> chart = List.of(new ChartPoint(yesterday, yesterday, 0, 0), new ChartPoint(today, today, 1234, 56));
        StatsRange range = new StatsRange(LocalDate.of(2026, 9, 20), today, StatsPeriod.DAYS_30, null);
        when(visitorStats.resolve(null, null, null)).thenReturn(range);
        when(visitorStats.summarize(eq("localhost"), eq(range))).thenReturn(new VisitorStats(range,
                new Period(1234, 56), new Period(2000, 80), new Period(5000, 300), chart, 1234,
                List.of(new TopPost(POST_ID, "인기 글", "popular", 900, 40)),
                List.of(new NamedCount("www.google.com", 700)),
                List.of(new NamedCount("대한민국 (KR)", 800)),
                List.of(new NamedCount("Naver", 300))));

        mockMvc.perform(get("/admin").with(user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(allOf(
                        containsString("aria-current=\"page\">대시보드"),
                        containsString(">12</strong>"), containsString(">3</strong>"),
                        containsString(">1,234</strong>"), containsString("방문자 56명"),
                        containsString("height: 100%"), containsString("height: 0%"),
                        containsString("10월 19일 조회 1234회, 방문자 56명"),
                        containsString("최근 30일 일별 추이"), containsString("points=\"0.5,100.0 1.5,95.5\""),
                        containsString("href=\"/admin?period=30d\" aria-current=\"page\">30일"),
                        containsString("value=\"2026-09-20\""),
                        containsString("인기 글"), containsString("900회 · 40명"),
                        containsString("www.google.com"), containsString("700회"),
                        containsString("대한민국 (KR)"), containsString("800회"),
                        containsString("Naver"), containsString("300회"),
                        containsString("다음 글"), containsString("10.20 09:00"),
                        containsString("고친 초안"))));
    }

    /** 직접 고른 기간을 서비스에 넘기고, 잘못된 기간이면 안내 문구를 보여 준다. */
    @Test
    void passesCustomRangeAndShowsError() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(postEditor.summarize()).thenReturn(new PostSummary(0, 0, 0, 0, List.of(), List.of()));
        StatsRange custom = new StatsRange(from, to, null, null);
        StatsRange fallback = new StatsRange(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 19), StatsPeriod.DAYS_30, "시작일이 종료일보다 늦습니다.");
        when(visitorStats.resolve(null, from, to)).thenReturn(custom);
        when(visitorStats.resolve(null, to, from)).thenReturn(fallback);
        when(visitorStats.summarize(eq("localhost"), eq(custom))).thenReturn(empty(custom));
        when(visitorStats.summarize(eq("localhost"), eq(fallback))).thenReturn(empty(fallback));
        var admin = user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE));

        mockMvc.perform(get("/admin").param("from", "2026-09-01").param("to", "2026-09-30").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("2026.9.1~2026.9.30"),
                        not(containsString("aria-current=\"page\">30일")))));
        mockMvc.perform(get("/admin").param("from", "2026-09-30").param("to", "2026-09-01").with(admin))
                .andExpect(content().string(containsString("시작일이 종료일보다 늦습니다.")));
    }

    /**
     * @param range 조회 기간
     * @return 조회 기록이 없는 통계
     */
    private static VisitorStats empty(StatsRange range) {
        ChartPoint point = new ChartPoint(range.from(), range.to(), 0, 0);
        return new VisitorStats(range, new Period(0, 0), new Period(0, 0), new Period(0, 0), List.of(point), 0,
                List.of(), List.of(), List.of(), List.of());
    }
}
