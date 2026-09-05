package com.deanp.blog.visitor.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

/**
 * 방문자 요청에서 사용할 숫자형 IP 주소를 안전하게 파싱하고 익명화한다.
 */
final class VisitorIpAddress {

    private final byte[] addressBytes;
    private final boolean ipv4;

    private VisitorIpAddress(byte[] addressBytes, boolean ipv4) {
        this.addressBytes = addressBytes;
        this.ipv4 = ipv4;
    }

    /**
     * IP 리터럴 또는 포트가 붙은 IP 리터럴을 파싱한다.
     *
     * @param value 원본 주소
     * @return 파싱된 주소 또는 잘못된 입력이면 빈 Optional
     */
    static Optional<VisitorIpAddress> parse(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return Optional.empty();
        }
        if (normalizedValue.indexOf(':') >= 0) {
            return parseIpv6(normalizedValue);
        }
        return parseIpv4(normalizedValue);
    }

    /**
     * 주소를 IPv4 /24 또는 IPv6 /64 네트워크로 익명화한다.
     *
     * @return 익명화된 PostgreSQL inet 리터럴
     */
    String anonymized() {
        byte[] maskedBytes = Arrays.copyOf(addressBytes, addressBytes.length);
        if (ipv4) {
            maskedBytes[3] = 0;
            return formatIpv4(maskedBytes);
        }
        Arrays.fill(maskedBytes, 8, maskedBytes.length, (byte) 0);
        return formatIpv6(maskedBytes);
    }

    /**
     * 이 주소가 지정된 네트워크에 포함되는지 확인한다.
     *
     * @param network 네트워크 주소
     * @param prefixLength 네트워크 prefix 길이
     * @return 포함되면 true
     */
    boolean isWithin(VisitorIpAddress network, int prefixLength) {
        if (ipv4 != network.ipv4) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int index = 0; index < fullBytes; index++) {
            if (addressBytes[index] != network.addressBytes[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (addressBytes[fullBytes] & mask) == (network.addressBytes[fullBytes] & mask);
    }

    /**
     * 주소가 IPv4인지 확인한다.
     *
     * @return IPv4이면 true
     */
    boolean isIpv4() {
        return ipv4;
    }

    /**
     * 주소 계열에 맞는 최대 prefix 길이를 반환한다.
     *
     * @return IPv4는 32, IPv6는 128
     */
    int maxPrefixLength() {
        return ipv4 ? 32 : 128;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("[")) {
            int closingBracket = trimmed.indexOf(']');
            if (closingBracket <= 1) {
                return null;
            }
            String port = trimmed.substring(closingBracket + 1);
            if (!port.isEmpty() && !isPort(port.substring(1), port.startsWith(":"))) {
                return null;
            }
            return trimmed.substring(1, closingBracket);
        }
        if (trimmed.indexOf(':') >= 0
                && trimmed.indexOf(':') == trimmed.lastIndexOf(':')
                && trimmed.indexOf('.') > 0) {
            int separator = trimmed.lastIndexOf(':');
            String port = trimmed.substring(separator + 1);
            if (!isPort(port, true)) {
                return null;
            }
            return trimmed.substring(0, separator);
        }
        return trimmed;
    }

    private static boolean isPort(String port, boolean hasPortSeparator) {
        if (!hasPortSeparator || port.isEmpty() || !port.matches("\\d{1,5}")) {
            return false;
        }
        try {
            return Integer.parseInt(port) <= 65535;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static Optional<VisitorIpAddress> parseIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }
        byte[] bytes = new byte[4];
        try {
            for (int index = 0; index < octets.length; index++) {
                if (octets[index].isEmpty() || !octets[index].matches("\\d{1,3}")) {
                    return Optional.empty();
                }
                int octet = Integer.parseInt(octets[index]);
                if (octet > 255) {
                    return Optional.empty();
                }
                bytes[index] = (byte) octet;
            }
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return Optional.of(new VisitorIpAddress(bytes, true));
    }

    private static Optional<VisitorIpAddress> parseIpv6(String value) {
        if (!value.matches("[0-9A-Fa-f:.]+")) {
            return Optional.empty();
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet6Address)) {
                return Optional.empty();
            }
            return Optional.of(new VisitorIpAddress(address.getAddress(), false));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static String formatIpv4(byte[] bytes) {
        return (bytes[0] & 0xFF) + "."
                + (bytes[1] & 0xFF) + "."
                + (bytes[2] & 0xFF) + "."
                + (bytes[3] & 0xFF);
    }

    private static String formatIpv6(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < bytes.length; index += 2) {
            if (index > 0) {
                value.append(':');
            }
            int group = ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
            value.append(Integer.toHexString(group));
        }
        return value.toString();
    }
}
