package com.deanp.blog.visitor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * trusted proxy 주소와 CIDR 판정 규칙을 검증한다.
 */
class TrustedProxyMatcherTest {

    /**
     * 기본 설정은 어떤 forwarded 주소도 신뢰하지 않는다.
     */
    @Test
    void doesNotTrustForwardedAddressByDefault() {
        TrustedProxyMatcher matcher = new TrustedProxyMatcher("");

        assertThat(matcher.isTrusted("198.51.100.10")).isFalse();
    }

    /**
     * 설정된 IPv4 CIDR 내부의 proxy만 신뢰한다.
     */
    @Test
    void matchesConfiguredIpv4Cidr() {
        TrustedProxyMatcher matcher = new TrustedProxyMatcher("198.51.100.0/24");

        assertThat(matcher.isTrusted("198.51.100.10")).isTrue();
        assertThat(matcher.isTrusted("198.51.101.10")).isFalse();
    }

    /**
     * 여러 CIDR 설정과 IPv6 prefix를 함께 판정한다.
     */
    @Test
    void matchesMultipleIpv4AndIpv6Networks() {
        TrustedProxyMatcher matcher = new TrustedProxyMatcher(
                "198.51.100.10/32, 2001:db8:1234::/48"
        );

        assertThat(matcher.isTrusted("198.51.100.10")).isTrue();
        assertThat(matcher.isTrusted("2001:db8:1234:1::10")).isTrue();
        assertThat(matcher.isTrusted("2001:db8:9999::10")).isFalse();
    }

    /**
     * 잘못된 CIDR은 조용히 전체 proxy를 신뢰하는 대신 설정 오류로 거부한다.
     */
    @Test
    void rejectsInvalidTrustedProxyCidr() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TrustedProxyMatcher("not-a-network"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TrustedProxyMatcher("198.51.100.0/33"));
    }
}
