-- 개발·노트 두 게시판을 둔다. V10에서 없앤 게시판 계층을 단순한 형태로 다시 만든다.
-- 글과 카테고리가 게시판에 속하고, 시리즈는 대표 카테고리를 통해 게시판을 따른다.
-- 글 주소(slug)는 게시판과 관계없이 전체에서 고유한 채로 둔다.
SET search_path TO blog, public;

CREATE TABLE board (
    id              uuid         PRIMARY KEY,
    slug            varchar(100) NOT NULL,
    name            varchar(100) NOT NULL,
    description     text,
    sort_order      integer      NOT NULL DEFAULT 0,
    status          varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_board_slug UNIQUE (slug)
);

-- 목록 주소는 /{slug}이다. 기존 글은 모두 개발 게시판이 된다.
INSERT INTO board (id, slug, name, description, sort_order) VALUES
    ('00000000-0000-4000-8000-000000000001', 'dev', '개발', '개발하며 배운 것들, 만들어 가는 이야기.', 0),
    ('00000000-0000-4000-8000-000000000002', 'notes', '노트', '컨퍼런스 후기처럼 개발 밖의 정보와 생각을 나눕니다.', 1);

-- 게시판을 정하지 않고 넣은 행은 개발 게시판이 되도록 기본값을 둔다. 애플리케이션은 항상 게시판을 지정한다.
ALTER TABLE post_category ADD COLUMN board_id uuid NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE post_category ADD CONSTRAINT fk_post_category_board FOREIGN KEY (board_id) REFERENCES board (id);

ALTER TABLE post_detail ADD COLUMN board_id uuid NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE post_detail ADD CONSTRAINT fk_post_detail_board FOREIGN KEY (board_id) REFERENCES board (id);
CREATE INDEX idx_post_detail_board_status_published ON post_detail (board_id, status, published_at DESC);

-- 운영 글 중 노트로 옮기기로 정한 두 편. 카테고리·시리즈가 없는 글만 옮겨 게시판 규칙과 어긋나지 않게 한다.
-- 다른 DB에는 이 주소의 글이 없으므로 아무것도 바뀌지 않는다.
UPDATE post_detail SET board_id = '00000000-0000-4000-8000-000000000002'
WHERE slug IN ('AWS-Summit-Seoul-2026-후기-2일차', 'PLEX-파일-볼륨-변경시-DB-수정-방법')
  AND category_id IS NULL
  AND series_id IS NULL;
