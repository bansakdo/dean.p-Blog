package com.deanp.blog.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기동 시 관리자 계정을 준비할 때 쓰는 외부 설정이다.
 *
 * @param loginId 관리자 로그인 아이디, 비어 있으면 계정을 준비하지 않는다
 * @param password 관리자 비밀번호 평문, 저장할 때는 해시한다
 */
@ConfigurationProperties("app.admin.bootstrap")
public record AdminBootstrapProperties(String loginId, String password) {
}
