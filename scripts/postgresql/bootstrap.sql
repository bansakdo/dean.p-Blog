/*
 * 스크립트의 구현 목적:
 * - dean.p 블로그 전용 PostgreSQL 로그인 역할, 데이터베이스, 스키마를 생성하고 소유권을 부여한다.
 * - 비밀번호를 파일에 저장하지 않고 psql 변수로 전달받는다.
 *
 * 디자인 패턴:
 * - 단순 초기화 스크립트. 동일 이름의 역할·데이터베이스가 있으면 재사용한다.
 *
 * usage:
 * - PostgreSQL 관리자 계정으로 실행한다.
 * - psql -v app_password='안전한 비밀번호' -f scripts/postgresql/bootstrap.sql postgres
 *
 * 관련 스크립트:
 * - 없음
 */

\set ON_ERROR_STOP on

\if :{?app_password}
\else
\echo '필수 변수 app_password가 없습니다.'
\echo "사용법: psql -v app_password='안전한 비밀번호' -f scripts/postgresql/bootstrap.sql postgres"
\quit
\endif

SELECT 'CREATE ROLE dean_p_blog_app LOGIN'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'dean_p_blog_app'
)\gexec

ALTER ROLE dean_p_blog_app
    WITH LOGIN
    PASSWORD :'app_password';

SELECT 'CREATE DATABASE dean_p_blog WITH OWNER dean_p_blog_app ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'dean_p_blog'
)\gexec

GRANT CONNECT, TEMPORARY
    ON DATABASE dean_p_blog
    TO dean_p_blog_app;

\connect dean_p_blog

CREATE SCHEMA IF NOT EXISTS blog
    AUTHORIZATION dean_p_blog_app;

ALTER SCHEMA blog
    OWNER TO dean_p_blog_app;

GRANT USAGE, CREATE
    ON SCHEMA blog
    TO dean_p_blog_app;

ALTER ROLE dean_p_blog_app
    IN DATABASE dean_p_blog
    SET search_path TO blog, public;
