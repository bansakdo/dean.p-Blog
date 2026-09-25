package com.deanp.blog.visitor.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * 익명 방문자를 식별하는 HTTP 쿠키를 읽고 필요한 경우 새 식별자를 발급한다.
 */
@Component
public class VisitorCookieIdentifier {

    /** 익명 방문자 식별자 쿠키 이름이다. */
    public static final String COOKIE_NAME = "deanp_visitor_id";

    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE_POLICY = "Lax";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    private final boolean cookieSecure;

    /**
     * 프로필 설정에 따라 Secure 속성을 제어하는 방문자 쿠키 식별자를 구성한다.
     *
     * @param cookieSecure 방문자 쿠키에 Secure 속성을 적용할지 여부
     */
    public VisitorCookieIdentifier(@Value("${app.visitor.cookie-secure:false}") boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * 요청 쿠키에서 유효한 방문자 UUID를 재사용하거나 새 UUID 쿠키를 응답에 추가한다.
     *
     * @param request 방문자 쿠키를 읽을 현재 HTTP 요청
     * @param response 새 방문자 쿠키를 설정할 현재 HTTP 응답
     * @return 현재 요청에서 사용할 방문자 UUID 문자열
     */
    public String identify(HttpServletRequest request, HttpServletResponse response) {
        return findVisitorId(request)
                .orElseGet(() -> issueVisitorId(response));
    }

    /**
     * 요청 쿠키에서 표준 UUID 형식의 방문자 식별자를 찾는다.
     *
     * @param request 방문자 쿠키를 읽을 현재 HTTP 요청
     * @return 소문자로 정규화된 UUID 문자열, 없거나 형식이 맞지 않으면 빈 값
     */
    private Optional<String> findVisitorId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .map(this::canonicalUuid)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * 응답에 새 방문자 UUID 쿠키를 추가한다.
     *
     * @param response 새 방문자 쿠키를 설정할 현재 HTTP 응답
     * @return 새로 발급한 방문자 UUID 문자열
     */
    private String issueVisitorId(HttpServletResponse response) {
        String visitorId = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, visitorId)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(SAME_SITE_POLICY)
                .path(COOKIE_PATH)
                .maxAge(COOKIE_MAX_AGE)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return visitorId;
    }

    /**
     * UUID 입력을 표준 36자 형식으로 엄격히 검증하고 소문자로 정규화한다.
     *
     * @param value 검사할 쿠키 값
     * @return 표준 UUID이면 정규화된 값, 아니면 빈 값
     */
    private Optional<String> canonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return Optional.empty();
        }

        try {
            UUID uuid = UUID.fromString(value);
            String canonicalValue = uuid.toString();
            return canonicalValue.equalsIgnoreCase(value)
                    ? Optional.of(canonicalValue)
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
