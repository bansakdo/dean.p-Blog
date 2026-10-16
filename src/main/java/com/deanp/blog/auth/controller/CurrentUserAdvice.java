package com.deanp.blog.auth.controller;

import com.deanp.blog.auth.service.BlogUserDetailsService;
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
     * @param authentication 현재 요청의 인증 정보
     * @return 익명이 아닌 인증된 사용자이면 true
     */
    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
