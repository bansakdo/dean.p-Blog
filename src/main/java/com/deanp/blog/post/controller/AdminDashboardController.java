package com.deanp.blog.post.controller;

import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.visitor.service.VisitorStatsService;
import com.deanp.blog.visitor.service.VisitorStatsService.StatsPeriod;
import com.deanp.blog.visitor.service.VisitorStatsService.StatsRange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * 로그인한 관리자가 처음 보는 대시보드로, 글 현황과 고른 기간(기본 최근 30일)의 조회 통계를 한 화면에 모은다.
 */
@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private final PostEditorService postEditor;
    private final VisitorStatsService visitorStats;

    /**
     * 글 현황과 조회 통계를 보여 준다. 화면은 브라우저 캐시에 남기지 않는다.
     *
     * @param period 기간 버튼 값(7d, 30d, 90d, 6m, 1y) 또는 null
     * @param from 직접 고른 시작일 또는 null
     * @param to 직접 고른 종료일 또는 null
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param request 블로그 자신의 호스트를 알아낼 현재 요청
     * @param response 현재 HTTP 응답
     * @return 대시보드 템플릿 이름
     */
    @GetMapping("/admin")
    public String dashboard(@RequestParam(required = false) String period,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        StatsRange range = visitorStats.resolve(period, from, to);
        model.addAttribute("posts", postEditor.summarize());
        model.addAttribute("stats", visitorStats.summarize(request.getServerName(), range));
        model.addAttribute("periods", StatsPeriod.values());
        model.addAttribute("pageTitle", "대시보드 — dean.p");
        return "admin/dashboard";
    }
}
