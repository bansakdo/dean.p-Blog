package com.deanp.blog.visitor.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 방문 추적 요청 정책의 자동화 요청·로그인 사용자·제외 네트워크 기준을 검증한다.
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
     * 로그인한 사용자(관리자)의 조회는 추적하지 않는다.
     */
    @Test
    void excludesLoggedInUser() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts/real-post");
        request.setUserPrincipal(() -> "admin");

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

    /**
     * 사설 대역 제외를 켜면 192.168.x·10.x·루프백·IPv6 내부 주소의 조회는 추적하지 않고, 공인 주소는 추적한다.
     */
    @Test
    void excludesPrivateNetworksWhenEnabled() {
        VisitorTrackingRequestPolicy excluding = new VisitorTrackingRequestPolicy(new TrustedProxyMatcher(""), true, "");

        assertThat(excluding.shouldTrack(from("192.168.65.1"))).isFalse();
        assertThat(excluding.shouldTrack(from("10.1.2.3"))).isFalse();
        assertThat(excluding.shouldTrack(from("172.27.0.1"))).isFalse();
        assertThat(excluding.shouldTrack(from("127.0.0.1"))).isFalse();
        assertThat(excluding.shouldTrack(from("::1"))).isFalse();
        assertThat(excluding.shouldTrack(from("fd00::5"))).isFalse();
        assertThat(excluding.shouldTrack(from("203.0.113.9"))).isTrue();
        assertThat(excluding.shouldTrack(from("172.32.0.1"))).isTrue();
        assertThat(policy.shouldTrack(from("192.168.65.1"))).isTrue();
    }

    /**
     * 따로 지정한 공인 IP 대역의 조회는 추적하지 않는다. 사설 대역 제외를 꺼도 지정한 대역은 뺀다.
     */
    @Test
    void excludesConfiguredNetworks() {
        VisitorTrackingRequestPolicy excluding = new VisitorTrackingRequestPolicy(new TrustedProxyMatcher(""), false,
                "198.51.100.7/32, 2001:db8::/32");

        assertThat(excluding.shouldTrack(from("198.51.100.7"))).isFalse();
        assertThat(excluding.shouldTrack(from("2001:db8::1"))).isFalse();
        assertThat(excluding.shouldTrack(from("198.51.100.8"))).isTrue();
        assertThat(excluding.shouldTrack(from("192.168.0.2"))).isTrue();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VisitorTrackingRequestPolicy(new TrustedProxyMatcher(""), true, "not-a-network"));
    }

    /**
     * trusted proxy에서 온 요청은 X-Forwarded-For의 실제 방문자 주소로 판단한다.
     */
    @Test
    void judgesForwardedClientAddressBehindTrustedProxy() {
        VisitorTrackingRequestPolicy excluding = new VisitorTrackingRequestPolicy(new TrustedProxyMatcher("10.0.0.0/8"), true,
                "198.51.100.7/32");
        MockHttpServletRequest owner = from("10.0.0.2");
        owner.addHeader("X-Forwarded-For", "198.51.100.7");
        MockHttpServletRequest visitor = from("10.0.0.2");
        visitor.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(excluding.shouldTrack(owner)).isFalse();
        assertThat(excluding.shouldTrack(visitor)).isTrue();
    }

    /**
     * @param remoteAddress 요청을 보낸 주소
     * @return 일반 브라우저의 글 조회 요청
     */
    private static MockHttpServletRequest from(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts/real-post");
        request.setRemoteAddr(remoteAddress);
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
        return request;
    }
}
