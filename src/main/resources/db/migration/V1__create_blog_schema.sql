-- dean-p-blog 초기 스키마
-- UUID v7 식별자는 애플리케이션에서 생성한다.
-- 실행 방식: Flyway가 blog 스키마에서 자동 실행한다.

CREATE SCHEMA IF NOT EXISTS blog;
SET search_path TO blog, public;

CREATE TABLE app_user (
    id              uuid         PRIMARY KEY,
    login_id        varchar(100) NOT NULL UNIQUE,
    password_hash   varchar(255) NOT NULL,
    name            varchar(100) NOT NULL,
    email           varchar(255) UNIQUE,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    last_login_at   timestamptz
);

CREATE TABLE auth_group (
    id              uuid         PRIMARY KEY,
    name            varchar(100) NOT NULL UNIQUE,
    description     text,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE menu (
    id              uuid         PRIMARY KEY,
    parent_id       uuid,
    name            varchar(100) NOT NULL,
    path            varchar(500),
    sort_order      integer      NOT NULL DEFAULT 0,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_menu_parent
        FOREIGN KEY (parent_id) REFERENCES menu (id)
);

CREATE TABLE common_code (
    id              uuid         PRIMARY KEY,
    code_group      varchar(100) NOT NULL UNIQUE,
    name            varchar(100) NOT NULL,
    description     text,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE common_code_detail (
    id              uuid         PRIMARY KEY,
    common_code_id  uuid         NOT NULL,
    code            varchar(100) NOT NULL,
    name            varchar(100) NOT NULL,
    sort_order      integer      NOT NULL DEFAULT 0,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_common_code_detail_code
        FOREIGN KEY (common_code_id) REFERENCES common_code (id),
    CONSTRAINT uq_common_code_detail_code
        UNIQUE (common_code_id, code)
);

CREATE TABLE board (
    id              uuid         PRIMARY KEY,
    board_code      varchar(100) NOT NULL UNIQUE,
    name            varchar(100) NOT NULL,
    description     text,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE board_category (
    id              uuid         PRIMARY KEY,
    board_id        uuid         NOT NULL,
    parent_id       uuid,
    name            varchar(100) NOT NULL,
    slug            varchar(150) NOT NULL,
    sort_order      integer      NOT NULL DEFAULT 0,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_category_board
        FOREIGN KEY (board_id) REFERENCES board (id),
    CONSTRAINT fk_board_category_parent
        FOREIGN KEY (parent_id) REFERENCES board_category (id),
    CONSTRAINT uq_board_category_slug
        UNIQUE (board_id, slug)
);

CREATE TABLE tag (
    id              uuid         PRIMARY KEY,
    name            varchar(100) NOT NULL UNIQUE,
    slug            varchar(150) NOT NULL UNIQUE,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE board_detail (
    id              uuid         PRIMARY KEY,
    board_id        uuid         NOT NULL,
    category_id     uuid,
    author_id       uuid         NOT NULL,
    slug            varchar(200) NOT NULL UNIQUE,
    title           varchar(300) NOT NULL,
    summary         text,
    content         text         NOT NULL,
    content_format  varchar(30)  NOT NULL DEFAULT 'MARKDOWN',
    status          varchar(30)  NOT NULL DEFAULT 'DRAFT',
    published_at    timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_detail_board
        FOREIGN KEY (board_id) REFERENCES board (id),
    CONSTRAINT fk_board_detail_category
        FOREIGN KEY (category_id) REFERENCES board_category (id),
    CONSTRAINT fk_board_detail_author
        FOREIGN KEY (author_id) REFERENCES app_user (id)
);

CREATE TABLE attached_files (
    id              uuid         PRIMARY KEY,
    original_name   varchar(500) NOT NULL,
    stored_name     varchar(500) NOT NULL,
    storage_path    text         NOT NULL,
    content_type    varchar(200),
    file_size       bigint       NOT NULL DEFAULT 0,
    checksum        char(64),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE user_group (
    user_id         uuid        NOT NULL,
    group_id        uuid        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_user_group_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_group_group
        FOREIGN KEY (group_id) REFERENCES auth_group (id) ON DELETE CASCADE
);

CREATE TABLE group_menu (
    group_id        uuid        NOT NULL,
    menu_id         uuid        NOT NULL,
    permission_code varchar(30) NOT NULL DEFAULT 'READ',
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, menu_id, permission_code),
    CONSTRAINT fk_group_menu_group
        FOREIGN KEY (group_id) REFERENCES auth_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_menu_menu
        FOREIGN KEY (menu_id) REFERENCES menu (id) ON DELETE CASCADE
);

CREATE TABLE board_tag (
    board_detail_id uuid        NOT NULL,
    tag_id          uuid        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (board_detail_id, tag_id),
    CONSTRAINT fk_board_tag_board_detail
        FOREIGN KEY (board_detail_id) REFERENCES board_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_board_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE TABLE board_attached_file (
    board_detail_id uuid        NOT NULL,
    attached_file_id uuid       NOT NULL,
    sort_order      integer     NOT NULL DEFAULT 0,
    PRIMARY KEY (board_detail_id, attached_file_id),
    CONSTRAINT fk_board_file_board_detail
        FOREIGN KEY (board_detail_id) REFERENCES board_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_board_file_attached_file
        FOREIGN KEY (attached_file_id) REFERENCES attached_files (id) ON DELETE CASCADE
);

CREATE TABLE board_revision (
    id              uuid         PRIMARY KEY,
    board_detail_id uuid         NOT NULL,
    revision_no     integer      NOT NULL,
    title           varchar(300) NOT NULL,
    summary         text,
    content         text         NOT NULL,
    content_format  varchar(30)  NOT NULL DEFAULT 'MARKDOWN',
    changed_by      uuid,
    change_type     varchar(30)  NOT NULL DEFAULT 'UPDATE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_revision_board_detail
        FOREIGN KEY (board_detail_id) REFERENCES board_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_board_revision_changed_by
        FOREIGN KEY (changed_by) REFERENCES app_user (id),
    CONSTRAINT uq_board_revision_number
        UNIQUE (board_detail_id, revision_no)
);

CREATE TABLE publish_history (
    id              uuid         PRIMARY KEY,
    commit_hash     varchar(64)  NOT NULL,
    file_path       text         NOT NULL,
    board_detail_id uuid,
    event_type      varchar(30)  NOT NULL,
    status          varchar(30)  NOT NULL DEFAULT 'PENDING',
    error_message   text,
    started_at      timestamptz,
    completed_at    timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_publish_history_board_detail
        FOREIGN KEY (board_detail_id) REFERENCES board_detail (id) ON DELETE SET NULL
);

CREATE TABLE audit_log (
    id              uuid         PRIMARY KEY,
    actor_user_id   uuid,
    action          varchar(100) NOT NULL,
    target_type     varchar(100),
    target_id       varchar(100),
    request_id      varchar(100),
    ip_address      inet,
    metadata        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_log_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE TABLE visitor (
    id              uuid         PRIMARY KEY,
    anonymous_key   varchar(128) NOT NULL UNIQUE,
    first_seen_at   timestamptz  NOT NULL DEFAULT now(),
    last_seen_at    timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE visitor_event (
    id              uuid         PRIMARY KEY,
    visitor_id      uuid,
    board_detail_id uuid,
    event_type      varchar(30)  NOT NULL,
    occurred_at     timestamptz  NOT NULL DEFAULT now(),
    ip_address      inet,
    country_code    char(2),
    referrer_host   varchar(255),
    search_engine   varchar(50),
    search_query    text,
    user_agent      text,
    request_id      varchar(100),
    CONSTRAINT fk_visitor_event_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitor (id) ON DELETE SET NULL,
    CONSTRAINT fk_visitor_event_board_detail
        FOREIGN KEY (board_detail_id) REFERENCES board_detail (id) ON DELETE SET NULL
);

CREATE TABLE visitor_daily_summary (
    id                    uuid         PRIMARY KEY,
    summary_date          date         NOT NULL,
    board_detail_id       uuid,
    country_code          char(2),
    search_engine         varchar(50),
    landing_count         bigint       NOT NULL DEFAULT 0,
    view_count            bigint       NOT NULL DEFAULT 0,
    unique_visitor_count  bigint       NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_visitor_summary_board_detail
        FOREIGN KEY (board_detail_id) REFERENCES board_detail (id) ON DELETE SET NULL
);

CREATE INDEX idx_board_detail_board_status
    ON board_detail (board_id, status, published_at DESC);

CREATE INDEX idx_board_detail_category
    ON board_detail (category_id, published_at DESC);

CREATE INDEX idx_board_detail_author
    ON board_detail (author_id);

CREATE INDEX idx_board_tag_tag
    ON board_tag (tag_id, board_detail_id);

CREATE INDEX idx_board_revision_detail_created
    ON board_revision (board_detail_id, created_at DESC);

CREATE INDEX idx_publish_history_commit
    ON publish_history (commit_hash, file_path);

CREATE INDEX idx_audit_log_created
    ON audit_log (created_at DESC);

CREATE INDEX idx_visitor_event_occurred
    ON visitor_event (occurred_at DESC);

CREATE INDEX idx_visitor_event_board_occurred
    ON visitor_event (board_detail_id, occurred_at DESC);

CREATE INDEX idx_visitor_event_search_engine
    ON visitor_event (search_engine, occurred_at DESC);

CREATE INDEX idx_visitor_daily_summary_dimensions
    ON visitor_daily_summary (summary_date, board_detail_id, country_code, search_engine);
