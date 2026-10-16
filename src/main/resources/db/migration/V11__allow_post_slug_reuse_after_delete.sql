-- 삭제한 글이 주소(slug)를 계속 차지하지 않도록, 삭제하지 않은 글끼리만 slug가 고유하게 바꾼다.
-- 기존 행은 모두 고유하므로 데이터 변경 없이 제약만 교체한다.

SET search_path TO blog, public;

ALTER TABLE post_detail DROP CONSTRAINT post_detail_slug_key;

CREATE UNIQUE INDEX ux_post_detail_slug_active
    ON post_detail (slug)
    WHERE status <> 'DELETED';
