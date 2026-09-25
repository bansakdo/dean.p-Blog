package com.deanp.blog.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CI 접속 대상 오류가 DB 연결 전에 차단되는지 검증한다. */
class CiDatabaseGateTest {
    private static final String URL = "jdbc:postgresql://192.0.2.10:15432/dean_p_blog_ci";

    /** 누락되거나 바뀐 대상은 네트워크 연결 없이 거부한다. */
    @Test
    void rejectsMissingOrWrongTargetBeforeConnecting() {
        assertThrows(IllegalStateException.class, () -> target(null, "dean_p_blog_app_ci", "192.0.2.10", "15432"));
        assertThrows(IllegalStateException.class, () -> target(
                "jdbc:postgresql://192.0.2.10:15432/dean_p_blog", "dean_p_blog_app_ci", "192.0.2.10", "15432"));
        assertThrows(IllegalStateException.class, () -> target(URL, "dean_p_blog_app", "192.0.2.10", "15432"));
        assertThrows(IllegalStateException.class, () -> target(URL + "?currentSchema=public",
                "dean_p_blog_app_ci", "192.0.2.10", "15432"));
        assertThrows(IllegalStateException.class, () -> target(URL, "dean_p_blog_app_ci", null, "15432"));
        assertThrows(IllegalStateException.class, () -> target(URL, "dean_p_blog_app_ci", "192.0.2.10", null));
        assertThrows(IllegalStateException.class, () -> target(URL, "dean_p_blog_app_ci", "192.0.2.10", "65536"));
        assertThrows(IllegalStateException.class, () -> target(URL, "dean_p_blog_app_ci", "192.0.2.11", "15432"));
        assertThrows(IllegalStateException.class, () -> target(URL, "dean_p_blog_app_ci", "192.0.2.10", "15433"));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.validateTarget(
                URL, "dean_p_blog_app_ci", "unused", "192.0.2.10", "15432", null, "5432"));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.validateTarget(
                URL, "dean_p_blog_app_ci", "unused", "192.0.2.10", "15432", "192.0.2.20", null));
        assertDoesNotThrow(() -> target(URL, "dean_p_blog_app_ci", "192.0.2.10", "15432"));
    }

    /** NAT 외부 포트가 달라도 실제 서버 주소·포트는 별도 기대값과 일치해야 한다. */
    @Test
    void checksActualServerAndRoleIdentity() {
        assertDoesNotThrow(() -> CiDatabaseGate.verifyIdentity(
                "dean_p_blog_ci", "dean_p_blog_app_ci", "192.0.2.20", 5432,
                false, "192.0.2.20", 5432));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.verifyIdentity(
                "dean_p_blog_ci", "dean_p_blog_app_ci", "192.0.2.21", 5432,
                false, "192.0.2.20", 5432));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.verifyIdentity(
                "dean_p_blog_ci", "dean_p_blog_app_ci", "192.0.2.20", 15432,
                false, "192.0.2.20", 5432));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.verifyIdentity(
                "dean_p_blog", "dean_p_blog_app_ci", "192.0.2.20", 5432,
                false, "192.0.2.20", 5432));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.verifyIdentity(
                "dean_p_blog_ci", "dean_p_blog_app", "192.0.2.20", 5432,
                false, "192.0.2.20", 5432));
        assertThrows(IllegalStateException.class, () -> CiDatabaseGate.verifyIdentity(
                "dean_p_blog_ci", "dean_p_blog_app_ci", "192.0.2.20", 5432,
                true, "192.0.2.20", 5432));
    }

    /** @param url JDBC URL @param user 계정 @param host 접속 허용 호스트 @param port 접속 허용 포트 */
    private void target(String url, String user, String host, String port) {
        CiDatabaseGate.validateTarget(url, user, "unused", host, port, "192.0.2.20", "5432");
    }
}
