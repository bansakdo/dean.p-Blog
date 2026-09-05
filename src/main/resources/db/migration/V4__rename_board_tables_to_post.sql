-- board 테이블 패밀리를 post 테이블 패밀리로 이름 변경
-- 이미 적용된 V1을 다시 쓰지 않고 기존 행, PK, FK, 인덱스를 보존한다.

SET search_path TO blog, public;

CREATE OR REPLACE FUNCTION pg_temp.rename_table_if_present(old_name text, new_name text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF to_regclass('blog.' || old_name) IS NOT NULL
       AND to_regclass('blog.' || new_name) IS NULL THEN
        EXECUTE format('ALTER TABLE blog.%I RENAME TO %I', old_name, new_name);
    END IF;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.rename_column_if_present(table_name text, old_name text, new_name text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'blog'
          AND c.table_name = rename_column_if_present.table_name
          AND c.column_name = old_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'blog'
          AND c.table_name = rename_column_if_present.table_name
          AND c.column_name = new_name
    ) THEN
        EXECUTE format('ALTER TABLE blog.%I RENAME COLUMN %I TO %I', table_name, old_name, new_name);
    END IF;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.rename_constraint_if_present(table_name text, old_name text, new_name text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace ns ON ns.oid = rel.relnamespace
        WHERE ns.nspname = 'blog'
          AND rel.relname = table_name
          AND con.conname = old_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace ns ON ns.oid = rel.relnamespace
        WHERE ns.nspname = 'blog'
          AND rel.relname = table_name
          AND con.conname = new_name
    ) THEN
        EXECUTE format('ALTER TABLE blog.%I RENAME CONSTRAINT %I TO %I', table_name, old_name, new_name);
    END IF;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.rename_index_if_present(old_name text, new_name text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF to_regclass('blog.' || old_name) IS NOT NULL
       AND to_regclass('blog.' || new_name) IS NULL THEN
        EXECUTE format('ALTER INDEX blog.%I RENAME TO %I', old_name, new_name);
    END IF;
END $$;

SELECT pg_temp.rename_table_if_present('board', 'post');
SELECT pg_temp.rename_table_if_present('board_category', 'post_category');
SELECT pg_temp.rename_table_if_present('board_detail', 'post_detail');
SELECT pg_temp.rename_table_if_present('board_tag', 'post_tag');
SELECT pg_temp.rename_table_if_present('board_attached_file', 'post_attached_file');
SELECT pg_temp.rename_table_if_present('board_revision', 'post_revision');

SELECT pg_temp.rename_column_if_present('post', 'board_code', 'post_code');
SELECT pg_temp.rename_column_if_present('post_category', 'board_id', 'post_id');
SELECT pg_temp.rename_column_if_present('post_detail', 'board_id', 'post_id');
SELECT pg_temp.rename_column_if_present('post_tag', 'board_detail_id', 'post_detail_id');
SELECT pg_temp.rename_column_if_present('post_attached_file', 'board_detail_id', 'post_detail_id');
SELECT pg_temp.rename_column_if_present('post_revision', 'board_detail_id', 'post_detail_id');
SELECT pg_temp.rename_column_if_present('publish_history', 'board_detail_id', 'post_detail_id');
SELECT pg_temp.rename_column_if_present('visitor_event', 'board_detail_id', 'post_detail_id');
SELECT pg_temp.rename_column_if_present('visitor_daily_summary', 'board_detail_id', 'post_detail_id');

SELECT pg_temp.rename_constraint_if_present('post', 'board_pkey', 'post_pkey');
SELECT pg_temp.rename_constraint_if_present('post', 'board_board_code_key', 'post_post_code_key');
SELECT pg_temp.rename_constraint_if_present('post_category', 'board_category_pkey', 'post_category_pkey');
SELECT pg_temp.rename_constraint_if_present('post_category', 'fk_board_category_board', 'fk_post_category_post');
SELECT pg_temp.rename_constraint_if_present('post_category', 'fk_board_category_parent', 'fk_post_category_parent');
SELECT pg_temp.rename_constraint_if_present('post_category', 'uq_board_category_slug', 'uq_post_category_slug');
SELECT pg_temp.rename_constraint_if_present('post_detail', 'board_detail_pkey', 'post_detail_pkey');
SELECT pg_temp.rename_constraint_if_present('post_detail', 'board_detail_slug_key', 'post_detail_slug_key');
SELECT pg_temp.rename_constraint_if_present('post_detail', 'fk_board_detail_board', 'fk_post_detail_post');
SELECT pg_temp.rename_constraint_if_present('post_detail', 'fk_board_detail_category', 'fk_post_detail_category');
SELECT pg_temp.rename_constraint_if_present('post_detail', 'fk_board_detail_author', 'fk_post_detail_author');
SELECT pg_temp.rename_constraint_if_present('post_tag', 'board_tag_pkey', 'post_tag_pkey');
SELECT pg_temp.rename_constraint_if_present('post_tag', 'fk_board_tag_board_detail', 'fk_post_tag_post_detail');
SELECT pg_temp.rename_constraint_if_present('post_tag', 'fk_board_tag_tag', 'fk_post_tag_tag');
SELECT pg_temp.rename_constraint_if_present('post_attached_file', 'board_attached_file_pkey', 'post_attached_file_pkey');
SELECT pg_temp.rename_constraint_if_present('post_attached_file', 'fk_board_file_board_detail', 'fk_post_attached_file_post_detail');
SELECT pg_temp.rename_constraint_if_present('post_attached_file', 'fk_board_file_attached_file', 'fk_post_attached_file_attached_file');
SELECT pg_temp.rename_constraint_if_present('post_revision', 'board_revision_pkey', 'post_revision_pkey');
SELECT pg_temp.rename_constraint_if_present('post_revision', 'fk_board_revision_board_detail', 'fk_post_revision_post_detail');
SELECT pg_temp.rename_constraint_if_present('post_revision', 'fk_board_revision_changed_by', 'fk_post_revision_changed_by');
SELECT pg_temp.rename_constraint_if_present('post_revision', 'uq_board_revision_number', 'uq_post_revision_number');
SELECT pg_temp.rename_constraint_if_present('publish_history', 'fk_publish_history_board_detail', 'fk_publish_history_post_detail');
SELECT pg_temp.rename_constraint_if_present('visitor_event', 'fk_visitor_event_board_detail', 'fk_visitor_event_post_detail');
SELECT pg_temp.rename_constraint_if_present('visitor_daily_summary', 'fk_visitor_summary_board_detail', 'fk_visitor_summary_post_detail');

SELECT pg_temp.rename_index_if_present('idx_board_detail_board_status', 'idx_post_detail_post_status');
SELECT pg_temp.rename_index_if_present('idx_board_detail_category', 'idx_post_detail_category');
SELECT pg_temp.rename_index_if_present('idx_board_detail_author', 'idx_post_detail_author');
SELECT pg_temp.rename_index_if_present('idx_board_tag_tag', 'idx_post_tag_tag');
SELECT pg_temp.rename_index_if_present('idx_board_revision_detail_created', 'idx_post_revision_detail_created');
SELECT pg_temp.rename_index_if_present('idx_visitor_event_board_occurred', 'idx_visitor_event_post_occurred');
