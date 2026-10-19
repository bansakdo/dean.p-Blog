package com.deanp.blog.visitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청을 보낸 주소가 신뢰된 reverse proxy 네트워크에 포함되는지 판별한다.
 */
@Component
public final class TrustedProxyMatcher {

    private final IpNetworks trustedNetworks;

    /**
     * 설정된 trusted proxy CIDR 목록을 파싱한다.
     *
     * @param configuredNetworks 쉼표로 구분된 IPv4/IPv6 CIDR 목록
     */
    public TrustedProxyMatcher(@Value("${app.visitor.trusted-proxies:}") String configuredNetworks) {
        this.trustedNetworks = IpNetworks.parse(configuredNetworks, "trusted proxy");
    }

    /**
     * 주소가 설정된 trusted proxy 네트워크에 포함되는지 확인한다.
     *
     * @param ipAddress 확인할 IP 주소
     * @return trusted proxy이면 true
     */
    public boolean isTrusted(String ipAddress) {
        return trustedNetworks.contains(ipAddress);
    }
}
