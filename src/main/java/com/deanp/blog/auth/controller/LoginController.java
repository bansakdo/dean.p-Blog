package com.deanp.blog.auth.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 로그인 화면을 렌더링한다. 로그인 처리 자체는 Spring Security가 맡는다.
 */
@Controller
public class LoginController {

    /**
     * CSRF 토큰은 처음 꺼낼 때 세션에 저장된다. 사이드 메뉴처럼 폼 앞부분이 길면 렌더링 중에 응답이 먼저
     * 전송돼 세션을 만들 수 없으므로, 렌더링 전에 토큰을 꺼내 세션을 미리 만든다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param csrfToken 로그인 폼에 넣을 CSRF 토큰
     * @return 로그인 템플릿 이름
     */
    @GetMapping("/login")
    public String login(Model model, CsrfToken csrfToken) {
        csrfToken.getToken();
        model.addAttribute("pageTitle", "로그인 — dean.p");
        return "login";
    }
}
