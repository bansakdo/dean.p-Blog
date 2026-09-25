package com.deanp.blog.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogSchemaDriftMigrationTest {

    private static final TestPostgresql POSTGRESQL = new TestPostgresql();

    private static final Map<String, ColumnExpectation> V9_CANONICAL_TEXT_COLUMNS = Map.ofEntries(
            Map.entry("app_user.login_id", new ColumnExpectation("character varying", 100)),
            Map.entry("app_user.name", new ColumnExpectation("character varying", 100)),
            Map.entry("app_user.status", new ColumnExpectation("character varying", 30)),
            Map.entry("auth_group.name", new ColumnExpectation("character varying", 100)),
            Map.entry("auth_group.description", new ColumnExpectation("text", null)),
            Map.entry("auth_group.status", new ColumnExpectation("character varying", 30)),
            Map.entry("menu.name", new ColumnExpectation("character varying", 100)),
            Map.entry("menu.path", new ColumnExpectation("character varying", 500)),
            Map.entry("menu.status", new ColumnExpectation("character varying", 30)),
            Map.entry("common_code.code_group", new ColumnExpectation("character varying", 100)),
            Map.entry("common_code.name", new ColumnExpectation("character varying", 100)),
            Map.entry("common_code.description", new ColumnExpectation("text", null)),
            Map.entry("common_code.status", new ColumnExpectation("character varying", 30)),
            Map.entry("common_code_detail.code", new ColumnExpectation("character varying", 100)),
            Map.entry("common_code_detail.name", new ColumnExpectation("character varying", 100)),
            Map.entry("common_code_detail.status", new ColumnExpectation("character varying", 30)),
            Map.entry("post.post_code", new ColumnExpectation("character varying", 100)),
            Map.entry("post.name", new ColumnExpectation("character varying", 100)),
            Map.entry("post.description", new ColumnExpectation("text", null)),
            Map.entry("post.status", new ColumnExpectation("character varying", 30)),
            Map.entry("post_category.name", new ColumnExpectation("character varying", 100)),
            Map.entry("post_category.slug", new ColumnExpectation("character varying", 150)),
            Map.entry("post_category.status", new ColumnExpectation("character varying", 30)),
            Map.entry("tag.name", new ColumnExpectation("character varying", 100)),
            Map.entry("tag.slug", new ColumnExpectation("character varying", 150)),
            Map.entry("post_detail.slug", new ColumnExpectation("character varying", 200)),
            Map.entry("post_detail.title", new ColumnExpectation("character varying", 300)),
            Map.entry("post_detail.summary", new ColumnExpectation("text", null)),
            Map.entry("post_detail.content", new ColumnExpectation("text", null)),
            Map.entry("post_detail.content_format", new ColumnExpectation("character varying", 30)),
            Map.entry("post_detail.status", new ColumnExpectation("character varying", 30)),
            Map.entry("attached_files.original_name", new ColumnExpectation("character varying", 500)),
            Map.entry("attached_files.stored_name", new ColumnExpectation("character varying", 500)),
            Map.entry("attached_files.storage_path", new ColumnExpectation("text", null)),
            Map.entry("attached_files.content_type", new ColumnExpectation("character varying", 200)),
            Map.entry("group_menu.permission_code", new ColumnExpectation("character varying", 30)),
            Map.entry("post_revision.title", new ColumnExpectation("character varying", 300)),
            Map.entry("post_revision.summary", new ColumnExpectation("text", null)),
            Map.entry("post_revision.content", new ColumnExpectation("text", null)),
            Map.entry("post_revision.content_format", new ColumnExpectation("character varying", 30)),
            Map.entry("post_revision.change_type", new ColumnExpectation("character varying", 30)),
            Map.entry("publish_history.commit_hash", new ColumnExpectation("character varying", 64)),
            Map.entry("publish_history.file_path", new ColumnExpectation("text", null)),
            Map.entry("publish_history.event_type", new ColumnExpectation("character varying", 30)),
            Map.entry("publish_history.status", new ColumnExpectation("character varying", 30)),
            Map.entry("publish_history.error_message", new ColumnExpectation("text", null)),
            Map.entry("audit_log.action", new ColumnExpectation("character varying", 100)),
            Map.entry("audit_log.target_type", new ColumnExpectation("character varying", 100)),
            Map.entry("audit_log.target_id", new ColumnExpectation("character varying", 100)),
            Map.entry("audit_log.request_id", new ColumnExpectation("character varying", 100)),
            Map.entry("visitor.anonymous_key", new ColumnExpectation("character varying", 128)),
            Map.entry("visitor_event.event_type", new ColumnExpectation("character varying", 30)),
            Map.entry("visitor_event.search_engine", new ColumnExpectation("character varying", 50)),
            Map.entry("visitor_event.search_query", new ColumnExpectation("text", null)),
            Map.entry("visitor_event.user_agent", new ColumnExpectation("text", null)),
            Map.entry("visitor_event.request_id", new ColumnExpectation("character varying", 100)),
            Map.entry("visitor_daily_summary.search_engine", new ColumnExpectation("character varying", 50)),
            Map.entry("post_series.slug", new ColumnExpectation("character varying", 150)),
            Map.entry("post_series.name", new ColumnExpectation("character varying", 150)),
            Map.entry("post_series.description", new ColumnExpectation("text", null)),
            Map.entry("post_series.status", new ColumnExpectation("character varying", 30))
    );

    @BeforeEach
    void resetDatabase() {
        POSTGRESQL.start();
        POSTGRESQL.verifyCi();
        flyway().clean();
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        POSTGRESQL.stop();
    }

    @Test
    @DisplayName("V3는 varchar로 드리프트된 IP/국가 컬럼의 정상 데이터를 대상 타입으로 변환한다")
    void migratesDriftedVarcharColumnsWithValidData() throws SQLException {
        migrateThroughV2();
        simulateOldVarcharColumns();

        executeSql("""
                INSERT INTO blog.audit_log (id, action, ip_address)
                VALUES ('11111111-1111-1111-1111-111111111111', 'LOGIN', '203.0.113.10');
                INSERT INTO blog.visitor_event (id, event_type, ip_address, country_code)
                VALUES ('22222222-2222-2222-2222-222222222222', 'VIEW', '2001:db8::1', 'KR');
                INSERT INTO blog.visitor_daily_summary (id, summary_date, country_code)
                VALUES ('33333333-3333-3333-3333-333333333333', DATE '2026-09-01', 'US');
                """);

        flyway().migrate();

        assertColumnTypes(Map.of(
                "audit_log.ip_address", new ColumnExpectation("inet", null),
                "visitor_event.ip_address", new ColumnExpectation("inet", null),
                "visitor_event.country_code", new ColumnExpectation("character", 2),
                "visitor_daily_summary.country_code", new ColumnExpectation("character", 2)
        ));
    }

    @Test
    @DisplayName("V3는 varchar IP 컬럼에 비 IP 값이 있으면 변환하지 않는다")
    void rejectsInvalidIpValuesBeforeConversion() throws SQLException {
        migrateThroughV2();
        simulateOldVarcharColumns();
        executeSql("""
                INSERT INTO blog.audit_log (id, action, ip_address)
                VALUES ('44444444-4444-4444-4444-444444444444', 'LOGIN', 'not-an-ip');
                """);

        assertThrows(FlywayException.class, () -> flyway().migrate());
        assertColumnTypes(Map.of("audit_log.ip_address", new ColumnExpectation("character varying", 255)));
    }

    @Test
    @DisplayName("V3는 varchar 국가 컬럼에 빈 값이나 2자 초과 값이 있으면 변환하지 않는다")
    void rejectsBlankOrOverLengthCountryCodesBeforeConversion() throws SQLException {
        migrateThroughV2();
        simulateOldVarcharColumns();
        executeSql("""
                INSERT INTO blog.visitor_event (id, event_type, ip_address, country_code)
                VALUES ('55555555-5555-5555-5555-555555555555', 'VIEW', '203.0.113.11', 'USA');
                INSERT INTO blog.visitor_daily_summary (id, summary_date, country_code)
                VALUES ('66666666-6666-6666-6666-666666666666', DATE '2026-09-01', ' ');
                """);

        assertThrows(FlywayException.class, () -> flyway().migrate());
        assertColumnTypes(Map.of(
                "visitor_event.country_code", new ColumnExpectation("character varying", 255),
                "visitor_daily_summary.country_code", new ColumnExpectation("character varying", 255)
        ));
    }

    @Test
    @DisplayName("V4는 board 테이블 패밀리를 데이터 손실 없이 post 테이블 패밀리로 변경한다")
    void renamesBoardTableFamilyToPostTablesWithoutLosingRows() throws SQLException {
        migrateThroughV3();
        insertBoardFamilyRows();

        flyway(MigrationVersion.fromVersion("4")).migrate();

        assertFalse(tableExists("board"));
        assertFalse(tableExists("board_category"));
        assertFalse(tableExists("board_detail"));
        assertFalse(tableExists("board_tag"));
        assertFalse(tableExists("board_attached_file"));
        assertFalse(tableExists("board_revision"));
        assertTrue(tableExists("post"));
        assertTrue(tableExists("post_category"));
        assertTrue(tableExists("post_detail"));
        assertTrue(tableExists("post_tag"));
        assertTrue(tableExists("post_attached_file"));
        assertTrue(tableExists("post_revision"));
        assertEquals("blog", scalarString("SELECT post_code FROM blog.post WHERE id = '77777777-7777-7777-7777-777777777777'"));
        assertEquals("hello-post", scalarString("SELECT slug FROM blog.post_detail WHERE id = '99999999-9999-9999-9999-999999999999'"));
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.post_tag
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                """));
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.post_attached_file
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                """));
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.post_revision
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                """));
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.publish_history
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                """));
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.visitor_event
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                """));
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.visitor_daily_summary
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                """));
        assertForeignKey("fk_post_detail_post", "post_detail", "post_id", "post");
        assertForeignKey("fk_post_tag_post_detail", "post_tag", "post_detail_id", "post_detail");
        assertForeignKey("fk_publish_history_post_detail", "publish_history", "post_detail_id", "post_detail");
    }


    @Test
    @DisplayName("V5는 기존 일별 방문 집계 중 날짜와 게시글이 중복된 행이 있으면 중단한다")
    void rejectsDuplicateVisitorDailySummaryDateAndPostBeforeUniqueIndex() throws SQLException {
        migrateThroughV4();
        executeSql("""
                INSERT INTO blog.visitor_daily_summary (id, summary_date, post_detail_id)
                VALUES
                    ('45454545-4545-4545-4545-454545454545', DATE '2026-09-01', '99999999-9999-9999-9999-999999999999'),
                    ('56565656-5656-5656-5656-565656565656', DATE '2026-09-01', '99999999-9999-9999-9999-999999999999')
                """);

        assertThrows(FlywayException.class, () -> flyway().migrate());
        assertFalse(indexExists("ux_visitor_daily_summary_date_post"));
    }

    @Test
    @DisplayName("V6는 기존 방문 이벤트를 기준으로 일별 집계 카운터를 보정한다")
    void reconcilesExistingVisitorDailySummaryCountsFromPostViewEvents() throws SQLException {
        migrateThroughV4();
        executeSql("""
                INSERT INTO blog.visitor (id, anonymous_key)
                VALUES ('abababab-abab-abab-abab-abababababab', 'second-anon');
                INSERT INTO blog.visitor_event (id, visitor_id, post_detail_id, event_type, occurred_at)
                VALUES
                    ('45454545-4545-4545-4545-454545454545', 'ffffffff-ffff-ffff-ffff-ffffffffffff', '99999999-9999-9999-9999-999999999999', 'POST_VIEW', TIMESTAMPTZ '2026-09-01 01:00:00+00'),
                    ('56565656-5656-5656-5656-565656565656', 'abababab-abab-abab-abab-abababababab', '99999999-9999-9999-9999-999999999999', 'POST_VIEW', TIMESTAMPTZ '2026-09-01 02:00:00+00');
                UPDATE blog.visitor_daily_summary
                SET landing_count = 99, view_count = 99, unique_visitor_count = 99
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                  AND summary_date = DATE '2026-09-01';
                """);

        flyway().migrate();

        Map<String, Integer> counts = querySummaryCounts();
        assertEquals(Map.of(
                "landing_count", 2,
                "view_count", 2,
                "unique_visitor_count", 2
        ), counts);
    }

    @Test
    @DisplayName("V6는 일별 집계 행이 없어도 방문 이벤트 기준으로 새 행을 생성한다")
    void createsMissingVisitorDailySummaryFromPostViewEvents() throws SQLException {
        migrateThroughV4();
        executeSql("""
                INSERT INTO blog.visitor_event (id, visitor_id, post_detail_id, event_type, occurred_at)
                VALUES ('67676767-6767-6767-6767-676767676767', 'ffffffff-ffff-ffff-ffff-ffffffffffff', '99999999-9999-9999-9999-999999999999', 'POST_VIEW', TIMESTAMPTZ '2026-09-02 01:00:00+00');
                """);

        flyway().migrate();

        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM blog.visitor_daily_summary
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                  AND summary_date = DATE '2026-09-02'
                """));
        assertEquals(1, scalarInteger("""
                SELECT landing_count
                FROM blog.visitor_daily_summary
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                  AND summary_date = DATE '2026-09-02'
                """));
        assertEquals(1, scalarInteger("""
                SELECT unique_visitor_count
                FROM blog.visitor_daily_summary
                WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'
                  AND summary_date = DATE '2026-09-02'
                """));
    }

    @Test
    @DisplayName("V8는 기존 공개 글을 보존하고 시리즈 컬럼을 미연결 상태로 추가한다")
    void addsSeriesSchemaWithoutChangingExistingPosts() throws SQLException {
        migrateThroughV7();

        flyway(MigrationVersion.fromVersion("8")).migrate();

        assertTrue(tableExists("post_series"));
        assertEquals("hello-post", scalarString("""
                SELECT slug
                FROM blog.post_detail
                WHERE id = '99999999-9999-9999-9999-999999999999'
                """));
        assertEquals(0, scalarInteger("""
                SELECT count(*)
                FROM blog.post_detail
                WHERE id = '99999999-9999-9999-9999-999999999999'
                  AND (series_id IS NOT NULL OR series_order IS NOT NULL)
                """));
        assertForeignKey("fk_post_detail_series", "post_detail", "series_id", "post_series");
        assertForeignKey("fk_post_series_category", "post_series", "category_id", "post_category");
    }

    @Test
    @DisplayName("V9는 새 스키마에서 문자 타입 정합성을 변경 없이 통과한다")
    void keepsFreshSchemaCanonicalTextTypes() throws SQLException {
        flyway(MigrationVersion.fromVersion("9")).migrate();

        assertEquals("V9__align_varchar255_schema_drift.sql", scalarString("""
                SELECT script
                FROM blog.flyway_schema_history
                WHERE version = '9'
                  AND success
                """));
        assertColumnTypes(V9_CANONICAL_TEXT_COLUMNS);
    }

    @Test
    @DisplayName("V9는 varchar(255)로 드리프트된 문자 컬럼을 V1-V8 정식 타입으로 복구하고 데이터를 보존한다")
    void restoresVarchar255DriftWithoutLosingData() throws SQLException {
        migrateThroughV8();
        simulateV9Varchar255Drift();
        executeSql("""
                UPDATE blog.app_user
                SET login_id = 'author-hangul', name = '작성자'
                WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
                UPDATE blog.post_detail
                SET title = '멀티바이트 제목', summary = '짧은 요약', content = '본문 한글 보존'
                WHERE id = '99999999-9999-9999-9999-999999999999';
                UPDATE blog.attached_files
                SET original_name = '첨부-한글.txt', storage_path = '/files/첨부-한글.txt'
                WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
                INSERT INTO blog.post_series (id, post_id, category_id, slug, name, description)
                VALUES (
                    '13131313-1313-1313-1313-131313131313',
                    '77777777-7777-7777-7777-777777777777',
                    '88888888-8888-8888-8888-888888888888',
                    'series-hangul',
                    '시리즈 이름',
                    '시리즈 설명'
                );
                """);

        flyway(MigrationVersion.fromVersion("9")).migrate();

        assertColumnTypes(V9_CANONICAL_TEXT_COLUMNS);
        assertEquals("작성자", scalarString("SELECT name FROM blog.app_user WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'"));
        assertEquals("본문 한글 보존", scalarString("SELECT content FROM blog.post_detail WHERE id = '99999999-9999-9999-9999-999999999999'"));
        assertEquals("/files/첨부-한글.txt", scalarString("SELECT storage_path FROM blog.attached_files WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'"));
        assertEquals("시리즈 설명", scalarString("SELECT description FROM blog.post_series WHERE id = '13131313-1313-1313-1313-131313131313'"));
    }

    @Test
    @DisplayName("V9는 축소 대상 컬럼의 초과 길이를 값 노출 없이 거부하고 롤백한다")
    void rejectsOverLengthNarrowingAndRollsBack() throws SQLException {
        String overLengthLoginId = "x".repeat(101);
        migrateThroughV8();
        simulateV9Varchar255Drift();
        executeSql("""
                UPDATE blog.app_user
                SET login_id = '%s'
                WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
                """.formatted(overLengthLoginId));

        FlywayException exception = assertThrows(FlywayException.class, () -> flyway().migrate());

        assertTrue(exception.getMessage().contains("blog.app_user.login_id"));
        assertTrue(exception.getMessage().contains("max length 101"));
        assertFalse(exception.getMessage().contains(overLengthLoginId));
        assertColumnTypes(Map.of(
                "app_user.login_id", new ColumnExpectation("character varying", 255),
                "auth_group.description", new ColumnExpectation("character varying", 255)
        ));
        assertEquals(0, scalarInteger("""
                SELECT count(*)
                FROM blog.flyway_schema_history
                WHERE version = '9'
                  AND success
                """));
        assertEquals(overLengthLoginId, scalarString("""
                SELECT login_id
                FROM blog.app_user
                WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
                """));
    }

    /** V10은 기존 글과 관련 이력을 유지하고 게시판 FK·컬럼만 제거한다. */
    @Test
    void removesPostContainerWithoutLosingRelatedRows() throws SQLException {
        migrateThroughV8();
        flyway(MigrationVersion.fromVersion("9")).migrate();
        executeSql("""
                INSERT INTO blog.post_series (id, post_id, category_id, slug, name)
                VALUES ('13131313-1313-1313-1313-131313131313',
                        '77777777-7777-7777-7777-777777777777',
                        '88888888-8888-8888-8888-888888888888', 'series', '시리즈');
                UPDATE blog.post_detail
                SET series_id = '13131313-1313-1313-1313-131313131313', series_order = 1
                WHERE id = '99999999-9999-9999-9999-999999999999';
                """);

        flyway().migrate();

        assertFalse(tableExists("post"));
        assertEquals(0, scalarInteger("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'blog' AND column_name = 'post_id'
                  AND table_name IN ('post_detail', 'post_category', 'post_series')
                """));
        assertEquals("hello-post", scalarString("SELECT slug FROM blog.post_detail WHERE id = '99999999-9999-9999-9999-999999999999'"));
        assertEquals("series", scalarString("SELECT slug FROM blog.post_series WHERE id = '13131313-1313-1313-1313-131313131313'"));
        assertEquals(1, scalarInteger("SELECT series_order FROM blog.post_detail WHERE id = '99999999-9999-9999-9999-999999999999'"));
        assertEquals(1, scalarInteger("SELECT count(*) FROM blog.post_tag"));
        assertEquals(1, scalarInteger("SELECT count(*) FROM blog.post_attached_file"));
        assertEquals(1, scalarInteger("SELECT count(*) FROM blog.post_revision"));
        assertEquals(1, scalarInteger("SELECT count(*) FROM blog.publish_history"));
        assertEquals(1, scalarInteger("SELECT count(*) FROM blog.visitor_event"));
        assertEquals(1, scalarInteger("SELECT count(*) FROM blog.visitor_daily_summary"));
        assertForeignKey("fk_post_detail_category", "post_detail", "category_id", "post_category");
        assertForeignKey("fk_post_detail_series", "post_detail", "series_id", "post_series");
        assertForeignKey("fk_post_series_category", "post_series", "category_id", "post_category");
        assertTrue(indexExists("idx_post_detail_status_published"));
    }

    /** 게시판별로 같은 slug가 있으면 V10은 데이터를 바꾸지 않고 중단한다. */
    @Test
    void rejectsDuplicateSlugsAcrossContainers() throws SQLException {
        migrateThroughV8();
        flyway(MigrationVersion.fromVersion("9")).migrate();
        executeSql("""
                INSERT INTO blog.post (id, post_code, name)
                VALUES ('14141414-1414-1414-1414-141414141414', 'other', '다른 게시판');
                INSERT INTO blog.post_category (id, post_id, name, slug)
                VALUES ('15151515-1515-1515-1515-151515151515',
                        '14141414-1414-1414-1414-141414141414', '중복', 'general');
                """);

        assertThrows(FlywayException.class, () -> flyway().migrate());
        assertTrue(tableExists("post"));
        assertEquals(2, scalarInteger("SELECT count(*) FROM blog.post"));
        assertEquals(2, scalarInteger("SELECT count(*) FROM blog.post_category WHERE slug = 'general'"));
        assertEquals(0, scalarInteger("SELECT count(*) FROM blog.flyway_schema_history WHERE version = '10' AND success"));
    }

    private void migrateThroughV2() {
        flyway(MigrationVersion.fromVersion("2")).migrate();
    }

    private void migrateThroughV3() {
        flyway(MigrationVersion.fromVersion("3")).migrate();
    }

    private void migrateThroughV4() throws SQLException {
        migrateThroughV3();
        insertBoardFamilyRows();
        flyway(MigrationVersion.fromVersion("4")).migrate();
    }

    private void migrateThroughV7() throws SQLException {
        migrateThroughV4();
        flyway(MigrationVersion.fromVersion("7")).migrate();
    }

    private void migrateThroughV8() throws SQLException {
        migrateThroughV7();
        flyway(MigrationVersion.fromVersion("8")).migrate();
    }

    private void simulateOldVarcharColumns() throws SQLException {
        executeSql("""
                ALTER TABLE blog.audit_log
                    ALTER COLUMN ip_address TYPE varchar(255)
                    USING ip_address::text;
                ALTER TABLE blog.visitor_event
                    ALTER COLUMN ip_address TYPE varchar(255)
                    USING ip_address::text;
                ALTER TABLE blog.visitor_event
                    ALTER COLUMN country_code TYPE varchar(255)
                    USING country_code::text;
                ALTER TABLE blog.visitor_daily_summary
                    ALTER COLUMN country_code TYPE varchar(255)
                    USING country_code::text;
                """);
    }

    private void simulateV9Varchar255Drift() throws SQLException {
        String sql = V9_CANONICAL_TEXT_COLUMNS.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .map(columnKey -> {
                    String[] parts = columnKey.split("\\.");
                    return """
                            ALTER TABLE blog.%s
                                ALTER COLUMN %s TYPE varchar(255)
                                USING %s::text;
                            """.formatted(parts[0], parts[1], parts[1]);
                })
                .collect(Collectors.joining());
        executeSql(sql);
    }

    private void insertBoardFamilyRows() throws SQLException {
        executeSql("""
                INSERT INTO blog.app_user (id, login_id, password_hash, name)
                VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'author', 'hash', 'Author');
                INSERT INTO blog.board (id, board_code, name)
                VALUES ('77777777-7777-7777-7777-777777777777', 'blog', 'Blog');
                INSERT INTO blog.board_category (id, board_id, name, slug)
                VALUES ('88888888-8888-8888-8888-888888888888', '77777777-7777-7777-7777-777777777777', 'General', 'general');
                INSERT INTO blog.board_detail (id, board_id, category_id, author_id, slug, title, content, status, published_at)
                VALUES (
                    '99999999-9999-9999-9999-999999999999',
                    '77777777-7777-7777-7777-777777777777',
                    '88888888-8888-8888-8888-888888888888',
                    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                    'hello-post',
                    'Hello',
                    'Body',
                    'PUBLISHED',
                    now()
                );
                INSERT INTO blog.tag (id, name, slug)
                VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Java', 'java');
                INSERT INTO blog.board_tag (board_detail_id, tag_id)
                VALUES ('99999999-9999-9999-9999-999999999999', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
                INSERT INTO blog.attached_files (id, original_name, stored_name, storage_path)
                VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'a.txt', 'a.txt', '/files/a.txt');
                INSERT INTO blog.board_attached_file (board_detail_id, attached_file_id, sort_order)
                VALUES ('99999999-9999-9999-9999-999999999999', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 1);
                INSERT INTO blog.board_revision (id, board_detail_id, revision_no, title, content, changed_by)
                VALUES ('dddddddd-dddd-dddd-dddd-dddddddddddd', '99999999-9999-9999-9999-999999999999', 1, 'Hello', 'Body', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
                INSERT INTO blog.publish_history (id, commit_hash, file_path, board_detail_id, event_type)
                VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'abcdef', 'posts/hello.md', '99999999-9999-9999-9999-999999999999', 'UPSERT');
                INSERT INTO blog.visitor (id, anonymous_key)
                VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'anon');
                INSERT INTO blog.visitor_event (id, visitor_id, board_detail_id, event_type)
                VALUES ('12121212-1212-1212-1212-121212121212', 'ffffffff-ffff-ffff-ffff-ffffffffffff', '99999999-9999-9999-9999-999999999999', 'VIEW');
                INSERT INTO blog.visitor_daily_summary (id, summary_date, board_detail_id)
                VALUES ('34343434-3434-3434-3434-343434343434', DATE '2026-09-01', '99999999-9999-9999-9999-999999999999');
                """);
    }

    private Flyway flyway() {
        return flyway(null);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRESQL.url(), POSTGRESQL.user(), POSTGRESQL.password())
                .defaultSchema("blog")
                .schemas("blog")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void executeSql(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.url(), POSTGRESQL.user(), POSTGRESQL.password());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertColumnTypes(Map<String, ColumnExpectation> expectedColumns) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.url(), POSTGRESQL.user(), POSTGRESQL.password());
             Statement statement = connection.createStatement()) {
            for (Map.Entry<String, ColumnExpectation> entry : expectedColumns.entrySet()) {
                String[] parts = entry.getKey().split("\\.");
                var resultSet = statement.executeQuery("""
                        SELECT data_type, character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = 'blog'
                          AND table_name = '%s'
                          AND column_name = '%s'
                        """.formatted(parts[0], parts[1]));
                resultSet.next();
                Integer length = (Integer) resultSet.getObject("character_maximum_length");
                assertEquals(entry.getValue(), new ColumnExpectation(resultSet.getString("data_type"), length));
            }
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        return scalarInteger("""
                SELECT count(*)
                FROM pg_class cls
                JOIN pg_namespace ns ON ns.oid = cls.relnamespace
                WHERE ns.nspname = 'blog'
                  AND cls.relkind = 'i'
                  AND cls.relname = '%s'
                """.formatted(indexName)) == 1;
    }

    private boolean tableExists(String tableName) throws SQLException {
        return scalarInteger("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'blog'
                  AND table_name = '%s'
                """.formatted(tableName)) == 1;
    }

    private void assertForeignKey(
            String constraintName,
            String tableName,
            String columnName,
            String referencedTableName
    ) throws SQLException {
        assertEquals(1, scalarInteger("""
                SELECT count(*)
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace ns ON ns.oid = rel.relnamespace
                JOIN pg_class ref_rel ON ref_rel.oid = con.confrelid
                JOIN unnest(con.conkey) AS cols(attnum) ON true
                JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = cols.attnum
                WHERE ns.nspname = 'blog'
                  AND con.conname = '%s'
                  AND rel.relname = '%s'
                  AND att.attname = '%s'
                  AND ref_rel.relname = '%s'
                """.formatted(constraintName, tableName, columnName, referencedTableName)));
    }

    private Map<String, Integer> querySummaryCounts() throws SQLException {
        String whereClause = " WHERE post_detail_id = '99999999-9999-9999-9999-999999999999'"
                + " AND summary_date = DATE '2026-09-01'";
        return Map.of(
                "landing_count", scalarInteger("SELECT landing_count FROM blog.visitor_daily_summary" + whereClause),
                "view_count", scalarInteger("SELECT view_count FROM blog.visitor_daily_summary" + whereClause),
                "unique_visitor_count", scalarInteger(
                        "SELECT unique_visitor_count FROM blog.visitor_daily_summary" + whereClause
                )
        );
    }

    private String scalarString(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.url(), POSTGRESQL.user(), POSTGRESQL.password());
             Statement statement = connection.createStatement()) {
            var resultSet = statement.executeQuery(sql);
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private int scalarInteger(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.url(), POSTGRESQL.user(), POSTGRESQL.password());
             Statement statement = connection.createStatement()) {
            var resultSet = statement.executeQuery(sql);
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private record ColumnExpectation(String dataType, Integer characterMaximumLength) {
    }
}
