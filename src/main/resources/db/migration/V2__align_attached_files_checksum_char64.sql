-- attached_files.checksum 타입 정합성 보정
-- 기존 V1 스키마 의도와 JPA 매핑(char(64))에 맞춘다.

SET search_path TO blog, public;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM blog.attached_files
        WHERE checksum IS NOT NULL
          AND length(checksum) > 64
    ) THEN
        RAISE EXCEPTION 'Cannot convert blog.attached_files.checksum to char(64): values longer than 64 characters exist';
    END IF;
END $$;

ALTER TABLE blog.attached_files
    ALTER COLUMN checksum TYPE char(64)
    USING checksum::char(64);
