package com.deanp.blog.visitor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 쉼표로 구분한 IPv4/IPv6 CIDR 목록과, 주소가 그중 하나에 포함되는지 판정하는 규칙이다.
 */
final class IpNetworks {

    private final List<Network> networks;

    /**
     * @param networks 파싱한 네트워크 목록
     */
    private IpNetworks(List<Network> networks) {
        this.networks = networks;
    }

    /**
     * CIDR 목록을 파싱한다. 잘못된 값은 조용히 넘기지 않고 설정 오류로 거부한다.
     *
     * @param configuredNetworks 쉼표로 구분된 CIDR 목록, 비어 있으면 아무 주소도 포함하지 않는다
     * @param label 오류 메시지에 쓸 설정 이름
     * @return 파싱한 네트워크 목록
     * @throws IllegalArgumentException CIDR 형식이 잘못된 경우
     */
    static IpNetworks parse(String configuredNetworks, String label) {
        if (configuredNetworks == null || configuredNetworks.isBlank()) {
            return new IpNetworks(List.of());
        }
        List<Network> networks = new ArrayList<>();
        for (String configuredNetwork : configuredNetworks.split(",", -1)) {
            String value = configuredNetwork.trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(label + " CIDR cannot be blank");
            }
            networks.add(Network.parse(value, label));
        }
        return new IpNetworks(List.copyOf(networks));
    }

    /**
     * @param ipAddress 확인할 IP 주소
     * @return 목록의 네트워크 중 하나에 포함되면 true, 주소가 잘못됐으면 false
     */
    boolean contains(String ipAddress) {
        Optional<VisitorIpAddress> parsedAddress = VisitorIpAddress.parse(ipAddress);
        return parsedAddress.isPresent() && networks.stream().anyMatch(network -> network.matches(parsedAddress.get()));
    }

    /**
     * CIDR 하나다.
     *
     * @param address 네트워크 주소
     * @param prefixLength prefix 길이
     */
    private record Network(VisitorIpAddress address, int prefixLength) {

        /**
         * @param value CIDR 문자열
         * @param label 오류 메시지에 쓸 설정 이름
         * @return 파싱한 CIDR
         */
        private static Network parse(String value, String label) {
            int separator = value.lastIndexOf('/');
            if (separator <= 0 || separator == value.length() - 1 || separator != value.indexOf('/')) {
                throw new IllegalArgumentException(label + " must be a CIDR: " + value);
            }
            VisitorIpAddress address = VisitorIpAddress.parse(value.substring(0, separator))
                    .orElseThrow(() -> new IllegalArgumentException("invalid " + label + " address: " + value));
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(value.substring(separator + 1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid " + label + " prefix: " + value, exception);
            }
            if (prefixLength < 0 || prefixLength > address.maxPrefixLength()) {
                throw new IllegalArgumentException("invalid " + label + " prefix: " + value);
            }
            return new Network(address, prefixLength);
        }

        /**
         * @param candidate 확인할 주소
         * @return 이 CIDR에 포함되면 true
         */
        private boolean matches(VisitorIpAddress candidate) {
            return candidate.isWithin(address, prefixLength);
        }
    }
}
