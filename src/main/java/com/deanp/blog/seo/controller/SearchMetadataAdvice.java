package com.deanp.blog.seo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;

/**
 * 공통 head가 쓰는 검색엔진·공유 미리보기 정보의 기본값을 모든 화면 모델에 담는다.
 * 글 상세처럼 더 구체적인 값이 있는 화면은 컨트롤러가 같은 이름으로 덮어쓴다.
 */
@ControllerAdvice
public class SearchMetadataAdvice {

    /** 사이트 전체 설명이다. 요약이 없는 글과 목록·소개 화면에 쓴다. */
    public static final String SITE_DESCRIPTION = "개발 공부를 하며 배운 것과 나만의 이야기를 기록하는 dean.p의 개인 공간입니다.";
    /** 검색 결과에 나오면 안 되는 화면 경로다. */
    private static final Set<String> NOINDEX_PATHS = Set.of("/login", "/search", "/error");

    @Value("${app.seo.indexable:true}")
    private boolean indexable;

    /**
     * @return 끝에 / 가 없는 사이트 주소. 공유 이미지처럼 절대 주소가 필요한 곳에 쓴다
     */
    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    /**
     * 색인하지 않을 화면을 고른다. 관리·로그인·검색·오류 화면이 해당한다.
     * 필터나 페이지 조건이 붙은 게시판 목록은 목록 컨트롤러가 같은 이름으로 덮어써 표시한다.
     *
     * @param request 현재 요청
     * @return 검색 결과에서 빼야 하면 true
     */
    @ModelAttribute("noindex")
    public boolean noindex(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !indexable || path.startsWith("/admin") || NOINDEX_PATHS.contains(path);
    }

    /**
     * 같은 화면이 쿼리 파라미터(사이드 메뉴 필터 유지 등)로 여러 주소를 갖더라도 대표 주소는 쿼리 없는 경로로 정한다.
     *
     * @param request 현재 요청
     * @return 쿼리를 뺀 절대 주소
     */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        return siteUrl() + request.getRequestURI();
    }

    /**
     * @return 검색 결과와 공유 미리보기에 쓸 기본 설명
     */
    @ModelAttribute("metaDescription")
    public String metaDescription() {
        return SITE_DESCRIPTION;
    }

    /**
     * @return 공유 미리보기의 기본 콘텐츠 종류
     */
    @ModelAttribute("ogType")
    public String ogType() {
        return "website";
    }
}
