package com.deanp.blog.auth.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아이디별 로그인 실패를 메모리에 세어 짧은 시간에 연속으로 틀리면 잠시 로그인을 막는다.
 * 서버를 재시작하면 기록이 사라지며, 단일 인스턴스 운영을 전제로 한다.
 */
@Service
public class LoginAttemptService {

    /** 이 횟수만큼 연속으로 틀리면 잠근다. */
    static final int MAX_FAILURES = 5;
    /** 직전 실패 후 이 시간이 지나면 실패를 새로 세며, 잠긴 뒤에는 마지막 실패부터 이 시간 동안 잠근다. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * 시스템 시계로 실패 시각을 기록한다.
     */
    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock 실패 시각과 잠금 만료를 판단할 시계
     */
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param loginId 로그인 아이디
     * @return 최근 실패가 너무 많아 지금 로그인을 막아야 하면 true
     */
    public boolean isLocked(String loginId) {
        Failures current = failures.get(key(loginId));
        return current != null && current.count() >= MAX_FAILURES && !current.isExpired(clock.instant());
    }

    /**
     * 아이디나 비밀번호가 틀린 로그인을 기록한다. 잠긴 동안의 시도는 이 이벤트가 아니므로 잠금을 늘리지 않는다.
     *
     * @param event 잘못된 자격 증명으로 실패한 인증 이벤트
     */
    @EventListener
    public void recordFailure(AuthenticationFailureBadCredentialsEvent event) {
        Instant now = clock.instant();

        // 만료된 기록을 지워 존재하지 않는 아이디로 반복 시도해도 기록이 계속 쌓이지 않게 한다.
        failures.values().removeIf(entry -> entry.isExpired(now));

        // 기간 안의 실패는 누적하고, 기간이 지났으면 새로 센다.
        failures.merge(key(event.getAuthentication().getName()), new Failures(1, now),
                (previous, first) -> new Failures(previous.count() + 1, now));
    }

    /**
     * 로그인에 성공하면 그 아이디의 실패 기록을 지운다.
     *
     * @param event 인증 성공 이벤트
     */
    @EventListener
    public void reset(AuthenticationSuccessEvent event) {
        failures.remove(key(event.getAuthentication().getName()));
    }

    /**
     * @param loginId 로그인 아이디
     * @return 대소문자와 앞뒤 공백 차이로 제한을 피하지 못하게 정리한 키
     */
    private String key(String loginId) {
        return loginId == null ? "" : loginId.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 한 아이디의 연속 실패 기록이다.
     *
     * @param count 연속 실패 횟수
     * @param lastFailedAt 마지막 실패 시각
     */
    private record Failures(int count, Instant lastFailedAt) {

        /**
         * @param now 현재 시각
         * @return 마지막 실패 후 잠금 시간이 지났으면 true
         */
        boolean isExpired(Instant now) {
            return !now.isBefore(lastFailedAt.plus(LOCK_DURATION));
        }
    }
}
