package com.deanp.blog.visitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 요청을 보낸 주소가 신뢰된 reverse proxy 네트워크에 포함되는지 판별한다.
 */
@Component
public final class TrustedProxyMatcher {

    private final List<TrustedNetwork> trustedNetworks;

    /**
     * 설정된 trusted proxy CIDR 목록을 파싱한다.
     *
     * @param configuredNetworks 쉼표로 구분된 IPv4/IPv6 CIDR 목록
     */
    public TrustedProxyMatcher(@Value("${app.visitor.trusted-proxies:}") String configuredNetworks) {
        this.trustedNetworks = parseNetworks(configuredNetworks);
    }

    /**
     * 주소가 설정된 trusted proxy 네트워크에 포함되는지 확인한다.
     *
     * @param ipAddress 확인할 IP 주소
     * @return trusted proxy이면 true
     */
    public boolean isTrusted(String ipAddress) {
        Optional<VisitorIpAddress> parsedAddress = VisitorIpAddress.parse(ipAddress);
        return parsedAddress.isPresent()
                && trustedNetworks.stream().anyMatch(network -> network.matches(parsedAddress.get()));
    }

    private static List<TrustedNetwork> parseNetworks(String configuredNetworks) {
        if (configuredNetworks == null || configuredNetworks.isBlank()) {
            return List.of();
        }
        List<TrustedNetwork> networks = new ArrayList<>();
        for (String configuredNetwork : configuredNetworks.split(",", -1)) {
            String value = configuredNetwork.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("trusted proxy CIDR cannot be blank");
            }
            networks.add(TrustedNetwork.parse(value));
        }
        return List.copyOf(networks);
    }

    private static final class TrustedNetwork {

        private final VisitorIpAddress address;
        private final int prefixLength;

        private TrustedNetwork(VisitorIpAddress address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }

        private static TrustedNetwork parse(String value) {
            int separator = value.lastIndexOf('/');
            if (separator <= 0 || separator == value.length() - 1 || separator != value.indexOf('/')) {
                throw new IllegalArgumentException("trusted proxy must be a CIDR: " + value);
            }
            VisitorIpAddress address = VisitorIpAddress.parse(value.substring(0, separator))
                    .orElseThrow(() -> new IllegalArgumentException("invalid trusted proxy address: " + value));
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(value.substring(separator + 1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid trusted proxy prefix: " + value, exception);
            }
            if (prefixLength < 0 || prefixLength > address.maxPrefixLength()) {
                throw new IllegalArgumentException("invalid trusted proxy prefix: " + value);
            }
            return new TrustedNetwork(address, prefixLength);
        }

        private boolean matches(VisitorIpAddress candidate) {
            return candidate.isWithin(address, prefixLength);
        }
    }
}
