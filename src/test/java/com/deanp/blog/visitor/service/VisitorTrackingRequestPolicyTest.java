package com.deanp.blog.visitor.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방문 추적 요청 정책의 자동화 요청 제외 기준을 검증한다.
 */
class VisitorTrackingRequestPolicyTest {

    private final VisitorTrackingRequestPolicy policy = new VisitorTrackingRequestPolicy();

    /**
     * 알려진 검색 크롤러 User-Agent는 추적하지 않는다.
     */
    @Test
    void excludesKnownBotUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts/real-post");
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; Googlebot/2.1)");

        assertThat(policy.shouldTrack(request)).isFalse();
    }

    /**
     * 헬스체크 URI는 User-Agent와 무관하게 추적하지 않는다.
     */
    @Test
    void excludesHealthCheckUri() {
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/health");
        MockHttpServletRequest actuatorHealth = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(policy.shouldTrack(health)).isFalse();
        assertThat(policy.shouldTrack(actuatorHealth)).isFalse();
    }

    /**
     * 일반 브라우저의 게시글 상세 요청은 추적한다.
     */
    @Test
    void tracksNormalPostDetailRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts/real-post");
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 Safari/605.1.15");

        assertThat(policy.shouldTrack(request)).isTrue();
    }
}
