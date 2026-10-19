package com.deanp.blog.auth.controller;

import com.deanp.blog.auth.persistence.entity.AppUser;
import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.auth.persistence.repository.GroupMenuRepository;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.auth.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.hamcrest.Matchers.containsString;

/** 실제 폼 로그인 흐름에서 연속 실패 잠금과 안내 화면이 동작하는지 검증한다. */
@WebMvcTest(LoginController.class)
@Import({SecurityConfiguration.class, BlogUserDetailsService.class, LoginAttemptService.class})
class LoginLockoutTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AppUserRepository users;

    @MockitoBean
    private GroupMenuRepository groupMenus;

    /** 잠금 기록이 테스트 사이에 이어지지 않도록 테스트마다 다른 아이디로 글 관리 권한 계정을 준비한다. */
    @BeforeEach
    void admins() {
        for (String loginId : new String[] {"admin", "locked-admin"}) {
            AppUser admin = AppUser.create(loginId, passwordEncoder.encode(PASSWORD), loginId);
            when(users.findByLoginId(loginId)).thenReturn(Optional.of(admin));
        }
        when(groupMenus.hasActivePermission(any(), eq(BlogUserDetailsService.POST_ADMIN_MENU_PATH),
                eq(BlogUserDetailsService.WRITE_PERMISSION))).thenReturn(true);
    }

    /** 맞는 비밀번호는 관리 대시보드로 보낸다. */
    @Test
    void logsInWithCorrectPassword() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password(PASSWORD))
                .andExpect(redirectedUrl("/admin"));
    }

    /** 다섯 번 틀린 뒤에는 맞는 비밀번호도 거부하고 잠금 안내로 보낸다. */
    @Test
    void locksAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(formLogin("/login").user("locked-admin").password("wrong"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(formLogin("/login").user("locked-admin").password(PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
        mockMvc.perform(get("/login").param("locked", ""))
                .andExpect(content().string(containsString("15분 뒤에 다시 시도하세요.")));
    }
}
