package com.deanp.blog.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional(readOnly = true)
@Testcontainers
class BlogSchemaVerificationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:16-alpine");

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "app_user",
            "auth_group",
            "menu",
            "common_code",
            "common_code_detail",
            "post",
            "post_category",
            "post_series",
            "tag",
            "post_detail",
            "attached_files",
            "user_group",
            "group_menu",
            "post_tag",
            "post_attached_file",
            "post_revision",
            "publish_history",
            "audit_log",
            "visitor",
            "visitor_event",
            "visitor_daily_summary"
    );

    private static final Map<String, ForeignKeyExpectation> EXPECTED_FOREIGN_KEYS = Map.ofEntries(
            Map.entry("fk_menu_parent", new ForeignKeyExpectation("menu", List.of("parent_id"), "menu", List.of("id"))),
            Map.entry("fk_common_code_detail_code", new ForeignKeyExpectation("common_code_detail", List.of("common_code_id"), "common_code", List.of("id"))),
            Map.entry("fk_post_category_post", new ForeignKeyExpectation("post_category", List.of("post_id"), "post", List.of("id"))),
            Map.entry("fk_post_category_parent", new ForeignKeyExpectation("post_category", List.of("parent_id"), "post_category", List.of("id"))),
            Map.entry("fk_post_detail_post", new ForeignKeyExpectation("post_detail", List.of("post_id"), "post", List.of("id"))),
            Map.entry("fk_post_detail_category", new ForeignKeyExpectation("post_detail", List.of("category_id"), "post_category", List.of("id"))),
            Map.entry("fk_post_detail_author", new ForeignKeyExpectation("post_detail", List.of("author_id"), "app_user", List.of("id"))),
            Map.entry("fk_post_representative_image", new ForeignKeyExpectation("post_detail", List.of("id", "representative_image_id"), "post_attached_file", List.of("post_detail_id", "attached_file_id"))),
            Map.entry("fk_post_series_post", new ForeignKeyExpectation("post_series", List.of("post_id"), "post", List.of("id"))),
            Map.entry("fk_post_series_category", new ForeignKeyExpectation("post_series", List.of("category_id"), "post_category", List.of("id"))),
            Map.entry("fk_post_series_post_category", new ForeignKeyExpectation("post_series", List.of("post_id", "category_id"), "post_category", List.of("post_id", "id"))),
            Map.entry("fk_post_detail_series", new ForeignKeyExpectation("post_detail", List.of("series_id"), "post_series", List.of("id"))),
            Map.entry("fk_post_detail_post_series", new ForeignKeyExpectation("post_detail", List.of("post_id", "series_id"), "post_series", List.of("post_id", "id"))),
            Map.entry("fk_user_group_user", new ForeignKeyExpectation("user_group", List.of("user_id"), "app_user", List.of("id"))),
            Map.entry("fk_user_group_group", new ForeignKeyExpectation("user_group", List.of("group_id"), "auth_group", List.of("id"))),
            Map.entry("fk_group_menu_group", new ForeignKeyExpectation("group_menu", List.of("group_id"), "auth_group", List.of("id"))),
            Map.entry("fk_group_menu_menu", new ForeignKeyExpectation("group_menu", List.of("menu_id"), "menu", List.of("id"))),
            Map.entry("fk_post_tag_post_detail", new ForeignKeyExpectation("post_tag", List.of("post_detail_id"), "post_detail", List.of("id"))),
            Map.entry("fk_post_tag_tag", new ForeignKeyExpectation("post_tag", List.of("tag_id"), "tag", List.of("id"))),
            Map.entry("fk_post_attached_file_post_detail", new ForeignKeyExpectation("post_attached_file", List.of("post_detail_id"), "post_detail", List.of("id"))),
            Map.entry("fk_post_attached_file_attached_file", new ForeignKeyExpectation("post_attached_file", List.of("attached_file_id"), "attached_files", List.of("id"))),
            Map.entry("fk_post_revision_post_detail", new ForeignKeyExpectation("post_revision", List.of("post_detail_id"), "post_detail", List.of("id"))),
            Map.entry("fk_post_revision_changed_by", new ForeignKeyExpectation("post_revision", List.of("changed_by"), "app_user", List.of("id"))),
            Map.entry("fk_publish_history_post_detail", new ForeignKeyExpectation("publish_history", List.of("post_detail_id"), "post_detail", List.of("id"))),
            Map.entry("fk_audit_log_actor", new ForeignKeyExpectation("audit_log", List.of("actor_user_id"), "app_user", List.of("id"))),
            Map.entry("fk_visitor_event_visitor", new ForeignKeyExpectation("visitor_event", List.of("visitor_id"), "visitor", List.of("id"))),
            Map.entry("fk_visitor_event_post_detail", new ForeignKeyExpectation("visitor_event", List.of("post_detail_id"), "post_detail", List.of("id"))),
            Map.entry("fk_visitor_summary_post_detail", new ForeignKeyExpectation("visitor_daily_summary", List.of("post_detail_id"), "post_detail", List.of("id")))
    );

    private static final Map<String, IndexExpectation> EXPECTED_INDEXES = Map.ofEntries(
            Map.entry("idx_post_detail_post_status", new IndexExpectation("post_detail", List.of("post_id", "status", "published_at"))),
            Map.entry("idx_post_detail_category", new IndexExpectation("post_detail", List.of("category_id", "published_at"))),
            Map.entry("idx_post_detail_author", new IndexExpectation("post_detail", List.of("author_id"))),
            Map.entry("idx_post_series_category", new IndexExpectation("post_series", List.of("category_id", "sort_order", "name"))),
            Map.entry("idx_post_detail_series", new IndexExpectation("post_detail", List.of("series_id", "series_order"))),
            Map.entry("ux_post_category_post_id_id", new IndexExpectation("post_category", List.of("post_id", "id"))),
            Map.entry("ux_post_series_post_id_id", new IndexExpectation("post_series", List.of("post_id", "id"))),
            Map.entry("ux_post_detail_series_order", new IndexExpectation("post_detail", List.of("series_id", "series_order"))),
            Map.entry("idx_post_tag_tag", new IndexExpectation("post_tag", List.of("tag_id", "post_detail_id"))),
            Map.entry("idx_post_revision_detail_created", new IndexExpectation("post_revision", List.of("post_detail_id", "created_at"))),
            Map.entry("idx_publish_history_commit", new IndexExpectation("publish_history", List.of("commit_hash", "file_path"))),
            Map.entry("idx_audit_log_created", new IndexExpectation("audit_log", List.of("created_at"))),
            Map.entry("idx_visitor_event_occurred", new IndexExpectation("visitor_event", List.of("occurred_at"))),
            Map.entry("idx_visitor_event_post_occurred", new IndexExpectation("visitor_event", List.of("post_detail_id", "occurred_at"))),
            Map.entry("idx_visitor_event_search_engine", new IndexExpectation("visitor_event", List.of("search_engine", "occurred_at"))),
            Map.entry("idx_visitor_daily_summary_dimensions", new IndexExpectation("visitor_daily_summary", List.of("summary_date", "post_detail_id", "country_code", "search_engine"))),
            Map.entry("ux_visitor_daily_summary_date_post", new IndexExpectation("visitor_daily_summary", List.of("summary_date", "post_detail_id")))
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRESQL::getJdbcUrl);
        registry.add("DB_USER", POSTGRESQL::getUsername);
        registry.add("DB_PASSWORD", POSTGRESQL::getPassword);
        registry.add("WAS_PORT", () -> "0");
    }

    @Autowired
    BlogSchemaVerificationTest(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Test
    @DisplayName("일회용 PostgreSQL에서 Flyway 블로그 스키마를 검증한다")
    void verifiesFlywayManagedBlogSchemaOnConfiguredDevPostgresql() throws SQLException {
        assertPostgresqlConnection();
        assertFlywayAppliedMigrations();
        assertTables();
        assertForeignKeys();
        assertIndexes();
        assertAlignedColumnTypes();
    }

    private void assertPostgresqlConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(5));
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    private void assertFlywayAppliedMigrations() {
        Map<String, String> appliedMigrations = jdbcTemplate.query("""
                SELECT version, script
                FROM blog.flyway_schema_history
                WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8', '9')
                  AND success
                """, rs -> {
                    Map<String, String> migrations = new java.util.HashMap<>();
                    while (rs.next()) {
                        migrations.put(rs.getString("version"), rs.getString("script"));
                    }
                    return migrations;
                });

        assertEquals(Map.of(
                "1", "V1__create_blog_schema.sql",
                "2", "V2__align_attached_files_checksum_char64.sql",
                "3", "V3__align_network_and_country_column_types.sql",
                "4", "V4__rename_board_tables_to_post.sql",
                "5", "V5__add_visitor_daily_summary_unique_post_day.sql",
                "6", "V6__reconcile_visitor_daily_summary_counts.sql",
                "7", "V7__add_representative_image.sql",
                "8", "V8__add_post_series.sql",
                "9", "V9__align_varchar255_schema_drift.sql"
        ), appliedMigrations);
    }

    private void assertTables() {
        Set<String> actualTables = new TreeSet<>(jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'blog'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class));

        assertEquals(new TreeSet<>(EXPECTED_TABLES), actualTables);
    }

    private void assertForeignKeys() {
        Map<String, ForeignKeyExpectation> actualForeignKeys = jdbcTemplate.query("""
                SELECT con.conname,
                       rel.relname AS table_name,
                       array_agg(att.attname ORDER BY cols.ordinality) AS column_names,
                       ref_rel.relname AS referenced_table_name,
                       array_agg(ref_att.attname ORDER BY cols.ordinality) AS referenced_column_names
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace ns ON ns.oid = rel.relnamespace
                JOIN pg_class ref_rel ON ref_rel.oid = con.confrelid
                JOIN unnest(con.conkey) WITH ORDINALITY AS cols(attnum, ordinality) ON true
                JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = cols.attnum
                JOIN pg_attribute ref_att ON ref_att.attrelid = ref_rel.oid AND ref_att.attnum = con.confkey[cols.ordinality]
                WHERE ns.nspname = 'blog'
                  AND con.contype = 'f'
                  AND con.conname = ANY (?)
                GROUP BY con.conname, rel.relname, ref_rel.relname
                """, ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", EXPECTED_FOREIGN_KEYS.keySet().toArray())),
                rs -> {
                    Map<String, ForeignKeyExpectation> foreignKeys = new java.util.HashMap<>();
                    while (rs.next()) {
                        foreignKeys.put(rs.getString("conname"), new ForeignKeyExpectation(
                                rs.getString("table_name"),
                                List.of((String[]) rs.getArray("column_names").getArray()),
                                rs.getString("referenced_table_name"),
                                List.of((String[]) rs.getArray("referenced_column_names").getArray())
                        ));
                    }
                    return foreignKeys;
                });

        assertEquals(EXPECTED_FOREIGN_KEYS, actualForeignKeys);
    }

    private void assertIndexes() {
        Map<String, IndexExpectation> actualIndexes = jdbcTemplate.query("""
                SELECT cls.relname AS index_name,
                       tbl.relname AS table_name,
                       array_agg(att.attname ORDER BY keys.ordinality) AS column_names
                FROM pg_index idx
                JOIN pg_class cls ON cls.oid = idx.indexrelid
                JOIN pg_class tbl ON tbl.oid = idx.indrelid
                JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
                JOIN unnest(idx.indkey) WITH ORDINALITY AS keys(attnum, ordinality) ON keys.attnum > 0
                JOIN pg_attribute att ON att.attrelid = tbl.oid AND att.attnum = keys.attnum
                WHERE ns.nspname = 'blog'
                  AND cls.relname = ANY (?)
                GROUP BY cls.relname, tbl.relname
                """, ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", EXPECTED_INDEXES.keySet().toArray())),
                rs -> {
                    Map<String, IndexExpectation> indexes = new java.util.HashMap<>();
                    while (rs.next()) {
                        indexes.put(rs.getString("index_name"), new IndexExpectation(
                                rs.getString("table_name"),
                                List.of((String[]) rs.getArray("column_names").getArray())
                        ));
                    }
                    return indexes;
                });

        assertEquals(EXPECTED_INDEXES, actualIndexes);
    }

    private void assertAlignedColumnTypes() {
        Map<String, ColumnExpectation> actualColumns = jdbcTemplate.query("""
                SELECT table_name || '.' || column_name AS column_key,
                       data_type,
                       character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'blog'
                  AND (table_name, column_name) IN (
                      ('attached_files', 'checksum'),
                      ('audit_log', 'ip_address'),
                      ('visitor_event', 'ip_address'),
                      ('visitor_event', 'country_code'),
                      ('visitor_daily_summary', 'country_code')
                  )
                """, rs -> {
            Map<String, ColumnExpectation> columns = new java.util.HashMap<>();
            while (rs.next()) {
                Integer length = (Integer) rs.getObject("character_maximum_length");
                columns.put(rs.getString("column_key"), new ColumnExpectation(
                        rs.getString("data_type"),
                        length
                ));
            }
            return columns;
        });

        assertEquals(Map.of(
                "attached_files.checksum", new ColumnExpectation("character", 64),
                "audit_log.ip_address", new ColumnExpectation("inet", null),
                "visitor_event.ip_address", new ColumnExpectation("inet", null),
                "visitor_event.country_code", new ColumnExpectation("character", 2),
                "visitor_daily_summary.country_code", new ColumnExpectation("character", 2)
        ), actualColumns);
    }

    private record ForeignKeyExpectation(
            String tableName,
            List<String> columnNames,
            String referencedTableName,
            List<String> referencedColumnNames
    ) {
    }

    private record IndexExpectation(String tableName, List<String> columnNames) {
    }

    private record ColumnExpectation(String dataType, Integer characterMaximumLength) {
    }
}
