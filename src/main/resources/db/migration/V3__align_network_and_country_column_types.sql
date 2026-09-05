-- audit/visitor 컬럼 타입 정합성 보정
-- 기존 V1 스키마 의도와 JPA 매핑(inet, char(2))에 맞춘다.

SET search_path TO blog, public;

CREATE OR REPLACE FUNCTION pg_temp.is_valid_inet(value text)
RETURNS boolean
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM value::inet;
    RETURN true;
EXCEPTION WHEN others THEN
    RETURN false;
END $$;

DO $$
DECLARE
    invalid_count bigint;
BEGIN
    SELECT count(*) INTO invalid_count
    FROM blog.audit_log
    WHERE ip_address IS NOT NULL
      AND NOT pg_temp.is_valid_inet(ip_address::text);

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Cannot convert blog.audit_log.ip_address to inet: % invalid non-IP values exist', invalid_count;
    END IF;

    SELECT count(*) INTO invalid_count
    FROM blog.visitor_event
    WHERE ip_address IS NOT NULL
      AND NOT pg_temp.is_valid_inet(ip_address::text);

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Cannot convert blog.visitor_event.ip_address to inet: % invalid non-IP values exist', invalid_count;
    END IF;

    SELECT count(*) INTO invalid_count
    FROM blog.visitor_event
    WHERE country_code IS NOT NULL
      AND (btrim(country_code::text) = '' OR length(country_code::text) > 2);

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Cannot convert blog.visitor_event.country_code to char(2): % blank or over-length values exist', invalid_count;
    END IF;

    SELECT count(*) INTO invalid_count
    FROM blog.visitor_daily_summary
    WHERE country_code IS NOT NULL
      AND (btrim(country_code::text) = '' OR length(country_code::text) > 2);

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Cannot convert blog.visitor_daily_summary.country_code to char(2): % blank or over-length values exist', invalid_count;
    END IF;
END $$;

ALTER TABLE blog.audit_log
    ALTER COLUMN ip_address TYPE inet
    USING ip_address::inet;

ALTER TABLE blog.visitor_event
    ALTER COLUMN ip_address TYPE inet
    USING ip_address::inet;

ALTER TABLE blog.visitor_event
    ALTER COLUMN country_code TYPE char(2)
    USING country_code::char(2);

ALTER TABLE blog.visitor_daily_summary
    ALTER COLUMN country_code TYPE char(2)
    USING country_code::char(2);
