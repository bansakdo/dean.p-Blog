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
