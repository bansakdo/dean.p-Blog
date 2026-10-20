package com.deanp.blog.post.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 사이드 메뉴의 게시판 하위 메뉴(카테고리)를 모든 화면에서 펼칠 수 있도록, 하위 메뉴를 직접 담지 않은 화면에 채운다.
 * 하위 메뉴를 불러오지 못해도 화면은 그대로 그리고, 사이드 메뉴의 게시판 항목만 보이지 않는다.
 */
@Configuration
public class SidebarFilterInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SidebarFilterInterceptor.class);

    private final ObjectProvider<SidebarFilterModel> sidebarFilters;

    /**
     * 컨트롤러 단위 테스트처럼 필터 모델 빈이 없는 환경에서는 아무것도 채우지 않도록 지연 조회한다.
     *
     * @param sidebarFilters 사이드 메뉴 필터 모델
     */
    public SidebarFilterInterceptor(ObjectProvider<SidebarFilterModel> sidebarFilters) {
        this.sidebarFilters = sidebarFilters;
    }

    /**
     * @param registry 인터셉터 등록부
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    /**
     * 화면을 그리기 직전에, 리다이렉트가 아니고 하위 메뉴가 아직 없는 모델에 게시판 하위 메뉴를 담는다.
     *
     * @param request 현재 요청
     * @param response 현재 응답
     * @param handler 처리한 핸들러
     * @param modelAndView 그릴 화면과 모델, 본문을 직접 쓰는 응답이면 null
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getModel().containsKey("boardMenus")) {
            return;
        }
        String viewName = modelAndView.getViewName();
        if (viewName == null || viewName.startsWith("redirect:") || viewName.startsWith("forward:")) {
            return;
        }
        SidebarFilterModel filters = sidebarFilters.getIfAvailable();
        if (filters == null) {
            return;
        }
        try {
            filters.addMenus(modelAndView.getModel());
        } catch (RuntimeException exception) {
            log.warn("사이드 메뉴 필터를 생략합니다. exceptionType={}", exception.getClass().getName());
        }
    }
}
