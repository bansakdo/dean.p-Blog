package com.deanp.blog.visitor.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방문 요청 메타데이터의 개인정보 최소화 규칙을 검증한다.
 */
class VisitorRequestMetadataTest {

    private final VisitorRequestMetadataFactory untrustedRequestFactory =
            new VisitorRequestMetadataFactory(new TrustedProxyMatcher(""));
    private final VisitorRequestMetadataFactory trustedProxyRequestFactory =
            new VisitorRequestMetadataFactory(new TrustedProxyMatcher("198.51.100.0/24"));

    /**
     * IPv4 원격 주소는 마지막 옥텟을 0으로 익명화한다.
     */
    @Test
    void masksRemoteIpv4AddressToNetwork() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.0");
    }

    /**
     * IPv6 원격 주소는 /64 네트워크까지만 남기고 하위 비트를 제거한다.
     */
    @Test
    void masksRemoteIpv6AddressToNetwork() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:abcd:12:3456:789a:bcde:f012");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("2001:db8:abcd:12:0:0:0:0");
    }

    /**
     * 압축된 IPv6 주소도 같은 /64 익명화 규칙을 적용한다.
     */
    @Test
    void masksCompressedIpv6AddressToNetwork() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:abcd:12::1");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("2001:db8:abcd:12:0:0:0:0");
    }

    /**
     * 원격 주소에 포트가 포함되어도 주소만 익명화한다.
     */
    @Test
    void masksIpv4RemoteAddressWithPort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42:8080");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.0");
    }

    /**
     * 해석할 수 없는 IP 주소는 저장하지 않는다.
     */
    @Test
    void dropsInvalidIpAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("not-an-ip");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isNull();
    }

    /**
     * 신뢰되지 않은 요청의 X-Forwarded-For는 무시한다.
     */
    @Test
    void ignoresForwardedAddressFromUntrustedRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.77");
        request.addHeader("X-Forwarded-For", "203.0.113.42, 198.51.100.10");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("198.51.100.0");
    }

    /**
     * trusted proxy 요청에서는 X-Forwarded-For의 첫 주소만 익명화한다.
     */
    @Test
    void masksFirstForwardedAddressFromTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.77");
        request.addHeader("X-Forwarded-For", "203.0.113.42, 198.51.100.10");

        VisitorRequestMetadata metadata = trustedProxyRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.0");
    }

    /**
     * trusted proxy가 전달한 잘못된 X-Forwarded-For는 proxy 주소로 안전하게 대체한다.
     */
    @Test
    void fallsBackToTrustedProxyAddressWhenForwardedAddressIsInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.77");
        request.addHeader("X-Forwarded-For", "not-an-ip");

        VisitorRequestMetadata metadata = trustedProxyRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("198.51.100.0");
    }

    /**
     * trusted proxy 체인에서는 오른쪽부터 검사해 첫 번째 비신뢰 주소를 클라이언트로 사용한다.
     */
    @Test
    void ignoresSpoofedForwardedAddressBeforeTrustedProxyChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.77");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.42, 198.51.100.10");

        VisitorRequestMetadata metadata = trustedProxyRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.0");
    }

    /**
     * X-Forwarded-For 체인에 잘못된 값이 있으면 전체 체인을 신뢰하지 않는다.
     */
    @Test
    void fallsBackToTrustedProxyWhenForwardedChainIsMalformed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.77");
        request.addHeader("X-Forwarded-For", "203.0.113.42, malformed, 198.51.100.10");

        VisitorRequestMetadata metadata = trustedProxyRequestFactory.from(request);

        assertThat(metadata.ipAddress()).isEqualTo("198.51.100.0");
    }

    /**
     * Referer는 URL 전체가 아니라 호스트만 저장한다.
     */
    @Test
    void storesOnlyReferrerHost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42");
        request.addHeader(HttpHeaders.REFERER, "https://search.example.com/path?q=secret#fragment");

        VisitorRequestMetadata metadata = untrustedRequestFactory.from(request);

        assertThat(metadata.referrerHost()).isEqualTo("search.example.com");
    }
}
