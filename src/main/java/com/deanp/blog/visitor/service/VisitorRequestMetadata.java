package com.deanp.blog.visitor.service;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 방문자 이벤트에 저장할 요청 메타데이터를 개인정보 최소화 규칙으로 정제한다.
 *
 * @param ipAddress 익명화된 IP 주소
 * @param referrerHost Referer의 호스트명
 * @param userAgent 정제된 User-Agent
 * @param requestId 요청 식별자
 * @param countryCode 접속 국가 ISO 3166-1 alpha-2 코드, 모르면 null
 */
public record VisitorRequestMetadata(
        String ipAddress,
        String referrerHost,
        String userAgent,
        String requestId,
        String countryCode
) {

    /** Cloudflare가 방문자 IP로 판단해 붙이는 국가 헤더 */
    static final String COUNTRY_HEADER = "CF-IPCountry";
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

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
        countryCode = normalizeCountryCode(countryCode);
    }

    /**
     * Referer 호스트가 검색 결과 화면이면 검색 엔진 이름을 돌려준다.
     *
     * @return 검색 엔진 이름, 검색 유입이 아니면 null
     */
    public String searchEngine() {
        return SearchEngines.fromHost(referrerHost);
    }

    /**
     * 빈 요청 메타데이터를 생성한다.
     *
     * @return 모든 값이 없는 메타데이터
     */
    public static VisitorRequestMetadata empty() {
        return new VisitorRequestMetadata(null, null, null, null, null);
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
                request.getHeader("X-Request-ID"),
                request.getHeader(COUNTRY_HEADER)
        );
    }

    /**
     * trusted proxy에서 온 요청이면 X-Forwarded-For 체인에서, 아니면 remote address에서 익명화 전 클라이언트 IP를 고른다.
     *
     * @param request HTTP 요청
     * @param trustedProxyMatcher trusted proxy 판별기
     * @return 클라이언트 IP
     */
    static String clientIpAddress(HttpServletRequest request, TrustedProxyMatcher trustedProxyMatcher) {
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

    /**
     * 두 글자 국가 코드만 남긴다. Cloudflare가 알 수 없음(XX)·Tor(T1)로 붙인 값과 형식이 다른 값은 버린다.
     *
     * @param value 국가 헤더 값
     * @return 대문자 두 글자 국가 코드 또는 null
     */
    private static String normalizeCountryCode(String value) {
        if (value == null) {
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        return COUNTRY_CODE.matcher(code).matches() && !"XX".equals(code) ? code : null;
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
