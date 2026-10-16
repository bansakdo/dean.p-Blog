package com.deanp.blog.auth.controller;

import com.deanp.blog.auth.service.BlogUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;

import java.util.Map;

/**
 * 글 관리 화면만 로그인과 권한을 요구하고 공개 화면은 그대로 열어 둔다.
 */
@Configuration
public class SecurityConfiguration {

    /**
     * 요청 경로별 접근 규칙과 폼 로그인을 구성한다.
     *
     * @param http Spring Security HTTP 설정
     * @return 구성된 보안 필터 체인
     * @throws Exception 보안 설정 구성에 실패한 경우
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/admin/**").hasAuthority(BlogUserDetailsService.POST_WRITE)
                        .anyRequest().permitAll())
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin/posts")
                        .failureHandler(loginFailureHandler()))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // 공개 화면의 정적 파일 캐시 동작을 바꾸지 않도록 캐시 금지 헤더는 관리 화면에서만 직접 붙인다.
                .headers(headers -> headers.cacheControl(cache -> cache.disable()));
        return http.build();
    }

    /**
     * 연속 실패로 잠긴 경우만 따로 안내하고, 나머지 실패는 아이디·비밀번호 오류로 안내한다.
     *
     * @return 실패 원인별로 로그인 화면 파라미터를 고르는 처리기
     */
    private ExceptionMappingAuthenticationFailureHandler loginFailureHandler() {
        ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl("/login?error");
        handler.setExceptionMappings(Map.of(LockedException.class.getName(), "/login?locked"));
        return handler;
    }

    /**
     * @return 비밀번호를 BCrypt로 해시하고 검증하는 인코더
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
