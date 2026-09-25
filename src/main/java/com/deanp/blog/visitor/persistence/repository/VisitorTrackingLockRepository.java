package com.deanp.blog.visitor.persistence.repository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL transaction advisory lock으로 방문자 집계 경합을 제어한다.
 */
@Repository
public class VisitorTrackingLockRepository {

    private static final String ADVISORY_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0::bigint))";

    private final Optional<JdbcTemplate> jdbcTemplate;

    /**
     * lock repository를 생성한다.
     *
     * @param jdbcTemplates 현재 트랜잭션에 참여하는 JDBC template provider
     */
    public VisitorTrackingLockRepository(ObjectProvider<JdbcTemplate> jdbcTemplates) {
        this.jdbcTemplate = Optional.ofNullable(jdbcTemplates.getIfAvailable());
    }

    /**
     * 같은 익명 식별자의 방문자 조회·생성을 직렬화한다.
     *
     * @param anonymousKey 익명 방문자 식별자
     */
    public void lockVisitor(String anonymousKey) {
        acquire("visitor:" + anonymousKey);
    }

    /**
     * 같은 게시글·UTC 날짜의 summary 갱신을 직렬화한다.
     *
     * @param summaryDate UTC 기준 집계 날짜
     * @param postDetailId 게시글 상세 ID
     */
    public void lockDailySummary(java.time.LocalDate summaryDate, UUID postDetailId) {
        acquire("summary:" + summaryDate + ":" + postDetailId);
    }

    private void acquire(String lockKey) {
        jdbcTemplate.ifPresent(template -> template.execute(
                ADVISORY_LOCK_SQL,
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setString(1, lockKey);
                    statement.execute();
                    return null;
                }
        ));
    }
}
