package com.deanp.blog.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 아이디별 연속 실패 잠금 규칙을 확인한다. */
class LoginAttemptServiceTest {

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService attempts = new LoginAttemptService(clock);

    /** 다섯 번째 실패부터 잠기고 대소문자·공백이 다른 아이디도 같은 계정으로 센다. */
    @Test
    void locksAfterMaxFailures() {
        fail("admin", 4);
        assertThat(attempts.isLocked("admin")).isFalse();

        fail(" ADMIN ", 1);
        assertThat(attempts.isLocked("admin")).isTrue();
        assertThat(attempts.isLocked("other")).isFalse();
    }

    /** 마지막 실패 후 잠금 시간이 지나면 풀리고 실패를 새로 센다. */
    @Test
    void unlocksAfterLockDuration() {
        fail("admin", LoginAttemptService.MAX_FAILURES);

        clock.advance(LoginAttemptService.LOCK_DURATION.minusSeconds(1));
        assertThat(attempts.isLocked("admin")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(attempts.isLocked("admin")).isFalse();

        fail("admin", 1);
        assertThat(attempts.isLocked("admin")).isFalse();
    }

    /** 로그인에 성공하면 그때까지의 실패를 지운다. */
    @Test
    void resetsOnSuccess() {
        fail("admin", LoginAttemptService.MAX_FAILURES - 1);

        attempts.reset(new AuthenticationSuccessEvent(UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of())));
        fail("admin", 1);

        assertThat(attempts.isLocked("admin")).isFalse();
    }

    /** @param loginId 로그인 아이디 @param times 실패 횟수 */
    private void fail(String loginId, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(new AuthenticationFailureBadCredentialsEvent(
                    UsernamePasswordAuthenticationToken.unauthenticated(loginId, "wrong"),
                    new BadCredentialsException("bad")));
        }
    }

    /** 테스트에서 원하는 만큼 시간을 앞으로 보내는 시계다. */
    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-15T00:00:00Z");

        /** @param duration 앞으로 보낼 시간 */
        void advance(Duration duration) {
            now = now.plus(duration);
        }

        /** @return UTC */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** @param zone 무시한다 @return 같은 시계 */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** @return 현재 테스트 시각 */
        @Override
        public Instant instant() {
            return now;
        }
    }
}
