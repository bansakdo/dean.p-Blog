-- legacy varchar(255) 드리프트를 V1-V8의 명시적 문자 타입으로 정렬한다.
-- 값 축소 가능성이 있는 컬럼은 ALTER 전에 최대 길이를 검사하고 값 자체는 오류에 노출하지 않는다.

SET search_path TO blog, public;
SET LOCAL lock_timeout = '5s';

CREATE TEMP TABLE v9_canonical_text_columns (
    table_name varchar(63) NOT NULL,
    column_name varchar(63) NOT NULL,
    data_type varchar(32) NOT NULL,
    character_maximum_length integer,
    canonical_sql varchar(32) NOT NULL,
    PRIMARY KEY (table_name, column_name)
) ON COMMIT DROP;

INSERT INTO v9_canonical_text_columns (
    table_name,
    column_name,
    data_type,
    character_maximum_length,
    canonical_sql
) VALUES
    ('app_user', 'login_id', 'character varying', 100, 'varchar(100)'),
    ('app_user', 'name', 'character varying', 100, 'varchar(100)'),
    ('app_user', 'status', 'character varying', 30, 'varchar(30)'),
    ('auth_group', 'name', 'character varying', 100, 'varchar(100)'),
    ('auth_group', 'description', 'text', NULL, 'text'),
    ('auth_group', 'status', 'character varying', 30, 'varchar(30)'),
    ('menu', 'name', 'character varying', 100, 'varchar(100)'),
    ('menu', 'path', 'character varying', 500, 'varchar(500)'),
    ('menu', 'status', 'character varying', 30, 'varchar(30)'),
    ('common_code', 'code_group', 'character varying', 100, 'varchar(100)'),
    ('common_code', 'name', 'character varying', 100, 'varchar(100)'),
    ('common_code', 'description', 'text', NULL, 'text'),
    ('common_code', 'status', 'character varying', 30, 'varchar(30)'),
    ('common_code_detail', 'code', 'character varying', 100, 'varchar(100)'),
    ('common_code_detail', 'name', 'character varying', 100, 'varchar(100)'),
    ('common_code_detail', 'status', 'character varying', 30, 'varchar(30)'),
    ('post', 'post_code', 'character varying', 100, 'varchar(100)'),
    ('post', 'name', 'character varying', 100, 'varchar(100)'),
    ('post', 'description', 'text', NULL, 'text'),
    ('post', 'status', 'character varying', 30, 'varchar(30)'),
    ('post_category', 'name', 'character varying', 100, 'varchar(100)'),
    ('post_category', 'slug', 'character varying', 150, 'varchar(150)'),
    ('post_category', 'status', 'character varying', 30, 'varchar(30)'),
    ('tag', 'name', 'character varying', 100, 'varchar(100)'),
    ('tag', 'slug', 'character varying', 150, 'varchar(150)'),
    ('post_detail', 'slug', 'character varying', 200, 'varchar(200)'),
    ('post_detail', 'title', 'character varying', 300, 'varchar(300)'),
    ('post_detail', 'summary', 'text', NULL, 'text'),
    ('post_detail', 'content', 'text', NULL, 'text'),
    ('post_detail', 'content_format', 'character varying', 30, 'varchar(30)'),
    ('post_detail', 'status', 'character varying', 30, 'varchar(30)'),
    ('attached_files', 'original_name', 'character varying', 500, 'varchar(500)'),
    ('attached_files', 'stored_name', 'character varying', 500, 'varchar(500)'),
    ('attached_files', 'storage_path', 'text', NULL, 'text'),
    ('attached_files', 'content_type', 'character varying', 200, 'varchar(200)'),
    ('group_menu', 'permission_code', 'character varying', 30, 'varchar(30)'),
    ('post_revision', 'title', 'character varying', 300, 'varchar(300)'),
    ('post_revision', 'summary', 'text', NULL, 'text'),
    ('post_revision', 'content', 'text', NULL, 'text'),
    ('post_revision', 'content_format', 'character varying', 30, 'varchar(30)'),
    ('post_revision', 'change_type', 'character varying', 30, 'varchar(30)'),
    ('publish_history', 'commit_hash', 'character varying', 64, 'varchar(64)'),
    ('publish_history', 'file_path', 'text', NULL, 'text'),
    ('publish_history', 'event_type', 'character varying', 30, 'varchar(30)'),
    ('publish_history', 'status', 'character varying', 30, 'varchar(30)'),
    ('publish_history', 'error_message', 'text', NULL, 'text'),
    ('audit_log', 'action', 'character varying', 100, 'varchar(100)'),
    ('audit_log', 'target_type', 'character varying', 100, 'varchar(100)'),
    ('audit_log', 'target_id', 'character varying', 100, 'varchar(100)'),
    ('audit_log', 'request_id', 'character varying', 100, 'varchar(100)'),
    ('visitor', 'anonymous_key', 'character varying', 128, 'varchar(128)'),
    ('visitor_event', 'event_type', 'character varying', 30, 'varchar(30)'),
    ('visitor_event', 'search_engine', 'character varying', 50, 'varchar(50)'),
    ('visitor_event', 'search_query', 'text', NULL, 'text'),
    ('visitor_event', 'user_agent', 'text', NULL, 'text'),
    ('visitor_event', 'request_id', 'character varying', 100, 'varchar(100)'),
    ('visitor_daily_summary', 'search_engine', 'character varying', 50, 'varchar(50)'),
    ('post_series', 'slug', 'character varying', 150, 'varchar(150)'),
    ('post_series', 'name', 'character varying', 150, 'varchar(150)'),
    ('post_series', 'description', 'text', NULL, 'text'),
    ('post_series', 'status', 'character varying', 30, 'varchar(30)');

DO $$
DECLARE
    current_column record;
    max_length integer;
    target_column record;
    target_table record;
BEGIN
    -- 길이 검사와 ALTER 사이에 쓰기가 끼어들지 않도록 먼저 잠근다.
    FOR target_table IN
        SELECT DISTINCT m.table_name
        FROM v9_canonical_text_columns m
        JOIN information_schema.columns c
          ON c.table_schema = 'blog'
         AND c.table_name = m.table_name
         AND c.column_name = m.column_name
        WHERE c.data_type <> m.data_type
           OR c.character_maximum_length IS DISTINCT FROM m.character_maximum_length
        ORDER BY m.table_name
    LOOP
        EXECUTE format('LOCK TABLE blog.%I IN ACCESS EXCLUSIVE MODE', target_table.table_name);
    END LOOP;

    FOR target_column IN
        SELECT *
        FROM v9_canonical_text_columns
        ORDER BY table_name, column_name
    LOOP
        SELECT c.data_type, c.character_maximum_length
        INTO current_column
        FROM information_schema.columns c
        WHERE c.table_schema = 'blog'
          AND c.table_name = target_column.table_name
          AND c.column_name = target_column.column_name;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Cannot align varchar(255) drift: missing column blog.%.%',
                target_column.table_name,
                target_column.column_name;
        END IF;

        IF target_column.character_maximum_length IS NOT NULL
           AND (
               current_column.data_type <> 'character varying'
               OR current_column.character_maximum_length IS NULL
               OR current_column.character_maximum_length > target_column.character_maximum_length
           ) THEN
            EXECUTE format(
                'SELECT max(char_length(%I::text)) FROM blog.%I',
                target_column.column_name,
                target_column.table_name
            )
            INTO max_length;

            IF max_length > target_column.character_maximum_length THEN
                RAISE EXCEPTION
                    'Cannot convert blog.%.% to %: max length % exceeds %',
                    target_column.table_name,
                    target_column.column_name,
                    target_column.canonical_sql,
                    max_length,
                    target_column.character_maximum_length;
            END IF;
        END IF;
    END LOOP;

    FOR target_column IN
        SELECT m.*
        FROM v9_canonical_text_columns m
        JOIN information_schema.columns c
          ON c.table_schema = 'blog'
         AND c.table_name = m.table_name
         AND c.column_name = m.column_name
        WHERE c.data_type <> m.data_type
           OR c.character_maximum_length IS DISTINCT FROM m.character_maximum_length
        ORDER BY m.table_name, m.column_name
    LOOP
        EXECUTE format(
            'ALTER TABLE blog.%I ALTER COLUMN %I TYPE %s',
            target_column.table_name,
            target_column.column_name,
            target_column.canonical_sql
        );
    END LOOP;
END $$;
