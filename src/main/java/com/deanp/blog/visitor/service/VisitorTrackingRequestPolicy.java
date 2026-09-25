package com.deanp.blog.visitor.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 방문자 쿠키 발급과 방문 이벤트 저장에서 제외할 요청 기준을 판단한다.
 */
@Component
public class VisitorTrackingRequestPolicy {

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

    /**
     * 기본 자동화 요청 제외 기준을 사용하는 방문 추적 정책을 생성한다.
     */
    public VisitorTrackingRequestPolicy() {
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
