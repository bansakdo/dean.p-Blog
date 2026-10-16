package com.deanp.blog.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;

/** 로컬은 일회용 컨테이너, CI는 검증된 전용 DB를 선택한다. */
public final class TestPostgresql {
    private final PostgreSQLContainer container;

    /** 테스트 실행 방식에 맞춰 접속을 준비한다. */
    public TestPostgresql() {
        container = Boolean.getBoolean("blog.ci.db") ? null : new PostgreSQLContainer("postgres:16-alpine");
    }

    /** 로컬 PostgreSQL 컨테이너를 한 번 시작한다. */
    public void start() {
        if (container != null && !container.isRunning()) {
            container.start();
        }
    }

    /**
     * Spring 컨텍스트 시작 전에 접속 대상을 검증하고 등록한다.
     * @param registry 동적 설정 저장소
     */
    public void register(DynamicPropertyRegistry registry) {
        start();
        verifyCi();
        registry.add("DB_URL", this::url);
        registry.add("DB_USER", this::user);
        registry.add("DB_PASSWORD", this::password);
        registry.add("WAS_PORT", () -> "0");
        if (container == null) {
            // CI는 모든 테스트 클래스가 한 DB 서버를 함께 쓰고, Spring이 캐시해 둔 컨텍스트마다 연결 풀이 남는다.
            // 기본 풀(연결 10개 유지)이 쌓이면 서버 연결 수를 넘으므로, 작게 잡고 쉬는 연결은 닫는다.
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
        }
    }

    /** @return JDBC URL */
    public String url() {
        return container == null ? System.getenv("CI_DB_URL") : container.getJdbcUrl();
    }

    /** @return DB 계정 */
    public String user() {
        return container == null ? System.getenv("CI_DB_USER") : container.getUsername();
    }

    /** @return DB 암호 */
    public String password() {
        return container == null ? System.getenv("CI_DB_PASSWORD") : container.getPassword();
    }

    /** DB 사용이 끝나면 로컬 컨테이너를 종료한다. */
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    /** CI에서는 Flyway 및 SQL 실행 이전에 접속 대상과 실제 서버 식별자를 확인한다. */
    public void verifyCi() {
        if (container == null) {
            try {
                CiDatabaseGate.verify(url(), user(), password());
            } catch (SQLException exception) {
                throw new IllegalStateException("CI DB preflight failed", exception);
            }
        }
    }
}
