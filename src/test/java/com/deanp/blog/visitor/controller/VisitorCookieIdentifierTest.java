package com.deanp.blog.visitor.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방문자 UUID 쿠키의 strict canonical 검증을 검증한다.
 */
class VisitorCookieIdentifierTest {

    private final VisitorCookieIdentifier identifier = new VisitorCookieIdentifier(false);

    /**
     * Java UUID parser가 관대하게 처리할 수 있는 비표준 표현은 거부한다.
     */
    @Test
    void rejectsNonCanonicalUuidRepresentations() {
        for (String value : new String[]{
                "1-1-1-1-1",
                "00000001-0001-0001-0001-00000000000z",
                "not-a-uuid",
                ""
        }) {
            MockHttpServletRequest request = requestWithCookie(value);
            MockHttpServletResponse response = new MockHttpServletResponse();

            String issuedValue = identifier.identify(request, response);

            assertThat(issuedValue).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(response.getHeader("Set-Cookie")).contains("deanp_visitor_id=" + issuedValue);
        }
    }

    /**
     * 대문자 canonical UUID는 소문자로 정규화해 기존 방문자를 재사용한다.
     */
    @Test
    void normalizesUppercaseCanonicalUuid() {
        MockHttpServletRequest request = requestWithCookie("123E4567-E89B-12D3-A456-426614174000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(identifier.identify(request, response))
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    private MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(VisitorCookieIdentifier.COOKIE_NAME, value));
        return request;
    }
}
