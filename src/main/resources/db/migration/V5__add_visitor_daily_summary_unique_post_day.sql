-- 방문자 일별 집계 중복 방지 제약 추가
-- 실행 방식: Flyway가 blog 스키마의 기존 visitor_daily_summary 테이블에 적용한다.

DO $$
DECLARE
    duplicate_count bigint;
BEGIN
    SELECT count(*)
    INTO duplicate_count
    FROM (
        SELECT summary_date, post_detail_id
        FROM blog.visitor_daily_summary
        WHERE post_detail_id IS NOT NULL
        GROUP BY summary_date, post_detail_id
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'Cannot add ux_visitor_daily_summary_date_post: % duplicate date/post groups exist', duplicate_count;
    END IF;
END $$;

CREATE UNIQUE INDEX ux_visitor_daily_summary_date_post
    ON blog.visitor_daily_summary (summary_date, post_detail_id)
    WHERE post_detail_id IS NOT NULL;
