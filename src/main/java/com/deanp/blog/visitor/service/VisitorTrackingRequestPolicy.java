package com.deanp.blog.visitor.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 방문자 쿠키 발급과 방문 이벤트 저장에서 제외할 요청 기준(헬스체크, 로그인 사용자, 제외 네트워크, 자동화 요청)을 판단한다.
 */
@Component
public class VisitorTrackingRequestPolicy {

    /** 사설·루프백·링크 로컬·CGNAT 대역. 운영에서 이 주소의 요청은 공개 방문이 아니라 내부망 접속이다. */
    static final String PRIVATE_NETWORKS = "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,169.254.0.0/16,"
            + "100.64.0.0/10,::1/128,fc00::/7,fe80::/10";

    private static final Set<String> HEALTH_CHECK_PATHS = Set.of("/health", "/actuator/health");
    private static final List<String> AUTOMATION_USER_AGENT_TOKENS = List.of(
            "bot",
            "crawler",
            "spider",
            "googlebot",
            "bingbot",
            "slurp",
            "duckduckbot",
            "baiduspider",
            "yandexbot",
            "facebookexternalhit",
            "twitterbot",
            "linkedinbot",
            "kube-probe",
            "prometheus",
            "uptimerobot",
            "pingdom",
            "statuscake",
            "datadog",
            "newrelic",
            "healthcheck",
            "monitoring"
    );

    private final TrustedProxyMatcher trustedProxyMatcher;
    private final IpNetworks excludedNetworks;

    /**
     * 기본 자동화 요청 제외 기준만 사용하고 IP로는 제외하지 않는 방문 추적 정책을 생성한다.
     */
    public VisitorTrackingRequestPolicy() {
        this(new TrustedProxyMatcher(""), false, "");
    }

    /**
     * 자동화 요청 제외 기준에 더해, 내부망과 설정한 네트워크에서 온 요청을 제외하는 방문 추적 정책을 생성한다.
     *
     * @param trustedProxyMatcher 클라이언트 IP를 고를 trusted proxy 판별기
     * @param excludePrivateNetworks 사설·루프백 대역({@link #PRIVATE_NETWORKS})의 요청을 제외할지 여부
     * @param excludedNetworks 쉼표로 구분된 추가 제외 CIDR 목록(예: 서버가 쓰는 공인 IP)
     */
    @Autowired
    public VisitorTrackingRequestPolicy(TrustedProxyMatcher trustedProxyMatcher,
                                        @Value("${app.visitor.exclude-private-networks:false}") boolean excludePrivateNetworks,
                                        @Value("${app.visitor.excluded-networks:}") String excludedNetworks) {
        this.trustedProxyMatcher = trustedProxyMatcher;
        String networks = excludePrivateNetworks ? PRIVATE_NETWORKS : "";
        if (excludedNetworks != null && !excludedNetworks.isBlank()) {
            networks = networks.isEmpty() ? excludedNetworks : networks + "," + excludedNetworks;
        }
        this.excludedNetworks = IpNetworks.parse(networks, "excluded network");
    }

    /**
     * 요청이 방문자 추적 대상인지 판단한다.
     *
     * @param request 검사할 현재 HTTP 요청
     * @return 추적 대상이면 {@code true}, 제외 대상이면 {@code false}
     */
    public boolean shouldTrack(HttpServletRequest request) {
        // 운영 상태 확인 엔드포인트는 쿠키 발급과 이벤트 저장 모두 제외한다.
        if (isHealthCheck(request.getRequestURI())) {
            return false;
        }

        // 로그인한 관리자가 자기 글을 확인하는 조회는 방문자 통계에 넣지 않는다. 익명 사용자는 principal이 없다.
        if (request.getUserPrincipal() != null) {
            return false;
        }

        // 내부망이나 서버 자신(설정한 공인 IP)에서 온 조회는 운영자의 접속이므로 넣지 않는다.
        if (excludedNetworks.contains(VisitorRequestMetadata.clientIpAddress(request, trustedProxyMatcher))) {
            return false;
        }

        // 알려진 봇, 크롤러, 모니터링 에이전트는 방문자 통계에 포함하지 않는다.
        return !hasAutomationUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
    }

    /**
     * 요청 URI가 헬스체크 경로인지 확인한다.
     *
     * @param requestUri 요청 URI
     * @return 헬스체크 경로이면 {@code true}, 아니면 {@code false}
     */
    private boolean isHealthCheck(String requestUri) {
        return HEALTH_CHECK_PATHS.contains(requestUri);
    }

    /**
     * User-Agent가 알려진 자동화 요청 토큰을 포함하는지 확인한다.
     *
     * @param userAgent User-Agent 헤더 값
     * @return 자동화 요청이면 {@code true}, 아니면 {@code false}
     */
    private boolean hasAutomationUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }

        String normalizedUserAgent = userAgent.toLowerCase(Locale.ROOT);
        return AUTOMATION_USER_AGENT_TOKENS.stream().anyMatch(normalizedUserAgent::contains);
    }
}
