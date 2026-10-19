package com.deanp.blog.auth.controller;

import com.deanp.blog.auth.service.BlogUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 공통 헤더의 사용자 메뉴가 쓰는 로그인 상태를 모든 화면 모델에 담는다.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    /**
     * @param authentication 현재 요청의 인증 정보, 없을 수 있다
     * @return 로그인한 사용자의 아이디, 로그인하지 않았으면 null
     */
    @ModelAttribute("currentUserName")
    public String currentUserName(Authentication authentication) {
        return isLoggedIn(authentication) ? authentication.getName() : null;
    }

    /**
     * @param authentication 현재 요청의 인증 정보, 없을 수 있다
     * @return 글 관리 권한이 있으면 true
     */
    @ModelAttribute("canWritePosts")
    public boolean canWritePosts(Authentication authentication) {
        return isLoggedIn(authentication) && authentication.getAuthorities().stream()
                .anyMatch(authority -> BlogUserDetailsService.POST_WRITE.equals(authority.getAuthority()));
    }

    /**
     * 사이드 메뉴의 관리 묶음에서 지금 보고 있는 화면을 표시하기 위해 요청 주소로 관리 메뉴를 고른다.
     *
     * @param request 현재 요청
     * @return dashboard·new·posts·categories·series·tags 중 하나, 관리 화면이 아니면 null
     */
    @ModelAttribute("adminMenu")
    public String adminMenu(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/admin")) {
            return "dashboard";
        }
        if (path.equals("/admin/posts/new")) {
            return "new";
        }
        for (String menu : new String[] {"posts", "categories", "series", "tags"}) {
            if (path.equals("/admin/" + menu) || path.startsWith("/admin/" + menu + "/")) {
                return menu;
            }
        }
        return null;
    }

    /**
     * @param authentication 현재 요청의 인증 정보
     * @return 익명이 아닌 인증된 사용자이면 true
     */
    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
