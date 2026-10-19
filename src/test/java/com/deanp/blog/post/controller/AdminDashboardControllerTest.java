package com.deanp.blog.post.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.post.service.PostEditorService.AdminPostRow;
import com.deanp.blog.post.service.PostEditorService.PostSummary;
import com.deanp.blog.visitor.service.VisitorStatsService;
import com.deanp.blog.visitor.service.VisitorStatsService.DayViews;
import com.deanp.blog.visitor.service.VisitorStatsService.Period;
import com.deanp.blog.visitor.service.VisitorStatsService.NamedCount;
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
        List<DayViews> daily = List.of(new DayViews(LocalDate.of(2026, 10, 18), 0, 0),
                new DayViews(LocalDate.of(2026, 10, 19), 1234, 56));
        when(visitorStats.summarize(eq("localhost"))).thenReturn(new VisitorStats(
                new Period(1234, 56), new Period(2000, 80), new Period(5000, 300), daily, 1234,
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
                        containsString("인기 글"), containsString("900회 · 40명"),
                        containsString("www.google.com"), containsString("700회"),
                        containsString("대한민국 (KR)"), containsString("800회"),
                        containsString("Naver"), containsString("300회"),
                        containsString("다음 글"), containsString("10.20 09:00"),
                        containsString("고친 초안"))));
    }
}
