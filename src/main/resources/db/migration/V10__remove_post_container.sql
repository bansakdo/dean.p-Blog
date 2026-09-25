-- 단일 블로그의 게시판 상위 계층을 제거하고 글·분류·시리즈는 보존한다.
SET search_path TO blog, public;

-- 게시판별로만 고유하던 slug가 전체 범위에서도 고유한지 먼저 검증한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM post_category GROUP BY slug HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'post_category.slug 중복으로 게시판 제거를 중단합니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM post_series GROUP BY slug HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'post_series.slug 중복으로 게시판 제거를 중단합니다.';
    END IF;
END $$;

ALTER TABLE post_category DROP CONSTRAINT uq_post_category_slug;
ALTER TABLE post_category ADD CONSTRAINT uq_post_category_slug UNIQUE (slug);
ALTER TABLE post_series DROP CONSTRAINT uq_post_series_slug;
ALTER TABLE post_series ADD CONSTRAINT uq_post_series_slug UNIQUE (slug);

ALTER TABLE post_detail DROP CONSTRAINT fk_post_detail_post_series;
ALTER TABLE post_series DROP CONSTRAINT fk_post_series_post_category;
ALTER TABLE post_detail DROP CONSTRAINT fk_post_detail_post;
ALTER TABLE post_series DROP CONSTRAINT fk_post_series_post;
ALTER TABLE post_category DROP CONSTRAINT fk_post_category_post;

DROP INDEX ux_post_category_post_id_id;
DROP INDEX ux_post_series_post_id_id;
DROP INDEX idx_post_detail_post_status;

ALTER TABLE post_detail DROP COLUMN post_id;
ALTER TABLE post_series DROP COLUMN post_id;
ALTER TABLE post_category DROP COLUMN post_id;
DROP TABLE post;

CREATE INDEX idx_post_detail_status_published ON post_detail (status, published_at DESC);
