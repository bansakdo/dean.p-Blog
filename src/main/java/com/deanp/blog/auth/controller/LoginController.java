package com.deanp.blog.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 로그인 화면을 렌더링한다. 로그인 처리 자체는 Spring Security가 맡는다.
 */
@Controller
public class LoginController {

    /**
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 로그인 템플릿 이름
     */
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "로그인 — dean.p");
        return "login";
    }
}
