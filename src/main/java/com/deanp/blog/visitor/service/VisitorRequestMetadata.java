package com.deanp.blog.visitor.service;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 방문자 이벤트에 저장할 요청 메타데이터를 개인정보 최소화 규칙으로 정제한다.
 *
 * @param ipAddress 익명화된 IP 주소
 * @param referrerHost Referer의 호스트명
 * @param userAgent 정제된 User-Agent
 * @param requestId 요청 식별자
 */
public record VisitorRequestMetadata(
        String ipAddress,
        String referrerHost,
        String userAgent,
        String requestId
) {

    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final int MAX_REQUEST_ID_LENGTH = 100;

    /**
     * 생성 시 외부에서 전달된 메타데이터를 다시 정제한다.
     */
    public VisitorRequestMetadata {
        ipAddress = anonymizeIpAddress(ipAddress);
        referrerHost = normalizeReferrerHost(referrerHost);
        userAgent = truncateAndRemoveControlCharacters(userAgent, MAX_USER_AGENT_LENGTH);
        requestId = truncateAndRemoveControlCharacters(requestId, MAX_REQUEST_ID_LENGTH);
    }

    /**
     * 빈 요청 메타데이터를 생성한다.
     *
     * @return 모든 값이 없는 메타데이터
     */
    public static VisitorRequestMetadata empty() {
        return new VisitorRequestMetadata(null, null, null, null);
    }

    /**
     * trusted proxy 정책을 적용해 HTTP 요청을 메타데이터로 변환한다.
     *
     * @param request HTTP 요청
     * @param trustedProxyMatcher trusted proxy 판별기
     * @return 정제된 요청 메타데이터
     */
    public static VisitorRequestMetadata from(HttpServletRequest request, TrustedProxyMatcher trustedProxyMatcher) {
        return new VisitorRequestMetadata(
                clientIpAddress(request, trustedProxyMatcher),
                request.getHeader("Referer"),
                request.getHeader("User-Agent"),
                request.getHeader("X-Request-ID")
        );
    }

    private static String clientIpAddress(HttpServletRequest request, TrustedProxyMatcher trustedProxyMatcher) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxyMatcher.isTrusted(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedAddress = clientAddressFromTrustedChain(
                request.getHeader("X-Forwarded-For"),
                trustedProxyMatcher
        );
        return forwardedAddress == null ? remoteAddress : forwardedAddress;
    }

    private static String clientAddressFromTrustedChain(
            String headerValue,
            TrustedProxyMatcher trustedProxyMatcher
    ) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }

        String[] values = headerValue.split(",", -1);
        String clientAddress = null;
        for (int index = values.length - 1; index >= 0; index--) {
            String value = values[index].trim();
            if (value.isEmpty() || VisitorIpAddress.parse(value).isEmpty()) {
                return null;
            }
            if (clientAddress == null && !trustedProxyMatcher.isTrusted(value)) {
                clientAddress = value;
            }
        }
        return clientAddress;
    }

    private static String anonymizeIpAddress(String value) {
        return VisitorIpAddress.parse(value)
                .map(VisitorIpAddress::anonymized)
                .orElse(null);
    }

    private static String normalizeReferrerHost(String value) {
        String sanitized = removeControlCharacters(value);
        if (sanitized == null || sanitized.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(sanitized.trim());
            String host = uri.getHost();
            if (host == null && isHostOnlyValue(sanitized)) {
                host = sanitized.trim();
            }
            return truncateAndRemoveControlCharacters(host, 255);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static boolean isHostOnlyValue(String value) {
        return value.indexOf('/') < 0
                && value.indexOf(':') < 0
                && value.matches("[A-Za-z0-9.-]+")
                && !value.startsWith(".")
                && !value.endsWith(".");
    }

    private static String truncateAndRemoveControlCharacters(String value, int maxLength) {
        String sanitized = removeControlCharacters(value);
        if (sanitized == null) {
            return null;
        }
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private static String removeControlCharacters(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(sanitized::appendCodePoint);
        String result = sanitized.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
