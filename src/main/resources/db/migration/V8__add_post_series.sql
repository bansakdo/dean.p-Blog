-- 게시글 시리즈와 공개 목록 정렬 정보를 추가한다.
-- 기존 게시글은 시리즈에 속하지 않은 상태로 보존한다.

SET search_path TO blog, public;

CREATE TABLE post_series (
    id              uuid         PRIMARY KEY,
    post_id         uuid         NOT NULL,
    category_id     uuid         NOT NULL,
    slug            varchar(150) NOT NULL,
    name            varchar(150) NOT NULL,
    description     text,
    sort_order      integer      NOT NULL DEFAULT 0,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_post_series_post
        FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_series_category
        FOREIGN KEY (category_id) REFERENCES post_category (id),
    CONSTRAINT uq_post_series_slug
        UNIQUE (post_id, slug)
);

CREATE UNIQUE INDEX ux_post_category_post_id_id
    ON post_category (post_id, id);

ALTER TABLE post_series ADD CONSTRAINT fk_post_series_post_category
    FOREIGN KEY (post_id, category_id)
    REFERENCES post_category (post_id, id);

CREATE UNIQUE INDEX ux_post_series_post_id_id
    ON post_series (post_id, id);

ALTER TABLE post_detail
    ADD COLUMN series_id uuid,
    ADD COLUMN series_order integer;

ALTER TABLE post_detail ADD CONSTRAINT fk_post_detail_series
    FOREIGN KEY (series_id)
    REFERENCES post_series (id);

ALTER TABLE post_detail ADD CONSTRAINT fk_post_detail_post_series
    FOREIGN KEY (post_id, series_id)
    REFERENCES post_series (post_id, id);

ALTER TABLE post_detail ADD CONSTRAINT ck_post_detail_series_order_pair
    CHECK (
        (series_id IS NULL AND series_order IS NULL)
        OR (series_id IS NOT NULL AND series_order IS NOT NULL AND series_order > 0)
    );

CREATE UNIQUE INDEX ux_post_detail_series_order
    ON post_detail (series_id, series_order)
    WHERE series_id IS NOT NULL;

CREATE INDEX idx_post_series_category
    ON post_series (category_id, sort_order, name);

CREATE INDEX idx_post_detail_series
    ON post_detail (series_id, series_order);
