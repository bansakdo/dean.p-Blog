package com.deanp.blog.visitor.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * HTTP 요청과 trusted proxy 정책을 방문자 저장용 메타데이터로 변환한다.
 */
@Component
public final class VisitorRequestMetadataFactory {

    private final TrustedProxyMatcher trustedProxyMatcher;

    /**
     * 메타데이터 factory를 생성한다.
     *
     * @param trustedProxyMatcher trusted proxy 판별기
     */
    public VisitorRequestMetadataFactory(TrustedProxyMatcher trustedProxyMatcher) {
        this.trustedProxyMatcher = trustedProxyMatcher;
    }

    /**
     * HTTP 요청에서 개인정보 최소화 규칙을 적용한 메타데이터를 생성한다.
     *
     * @param request HTTP 요청
     * @return 익명화·정제된 방문자 메타데이터
     */
    public VisitorRequestMetadata from(HttpServletRequest request) {
        return VisitorRequestMetadata.from(request, trustedProxyMatcher);
    }
}
