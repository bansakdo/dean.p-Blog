# Module and Package Design

## 목적

이 문서는 `docs/db`에 정의된 테이블을 애플리케이션 패키지에 배치하는 기준을 정의합니다.
현재 애플리케이션은 SSR 모놀리스이므로 별도 서비스로 분리하지 않고, 기능별 패키지 안에서 MVC 책임을 나눕니다.

## 기본 패키지 원칙

```text
com.deanp.blog
├── post             게시글 기능
├── auth             사용자·권한 기능
├── menu             메뉴 기능
├── code             공통 코드 기능
├── tag              태그 기능
├── media            첨부파일 기능
├── publishing       수정·발행 기능
├── visitor          방문자 통계 기능
└── audit             감사 로그 기능
```

기능 패키지 내부의 기본 구조는 다음과 같습니다.

```text
<feature>
├── controller       Controller, 요청·응답 DTO
├── service          유스케이스와 업무 로직
└── persistence      Entity, Spring Data Repository, Querydsl 조회
```

단순한 CRUD는 Spring Data Repository 메서드를 사용합니다. 검색, 다중 조건 필터, 조인, 정렬, 집계, 페이지네이션처럼 조회 조건이 복잡해지는 경우에는 해당 기능의 `persistence` 패키지에서 Querydsl을 기본으로 사용합니다.

## Controller 패키지 원칙

각 기능의 HTTP 경계는 해당 기능 안의 `controller` 패키지에 둡니다.

예: `com.deanp.blog.post.controller.BlogController`

Controller는 HTTP 입력, 검증 경계, Thymeleaf 모델 준비, view/API 응답 선택만 담당합니다.

전역 예외 처리나 공통 MVC 설정처럼 기능에 속하지 않는 웹 인프라가 생길 때만 별도 최상위 웹 설정 패키지를 추가합니다.

## 게시글 모듈

### `com.deanp.blog.post`

공개 블로그의 핵심 기능입니다. 게시글 목록, 상세, 작성, 수정, 발행 상태를 처리합니다.

패키지 예시:

```text
com.deanp.blog.post.controller
com.deanp.blog.post.service
com.deanp.blog.post.persistence
com.deanp.blog.post.persistence.query
```

바라보는 테이블:

- `post_detail`: 게시글 본문·제목·상태
- `post_category`: 대표 카테고리
- `post_series`: 시리즈와 대표 카테고리
- `post_revision`: 게시글 수정 버전
- `post_tag`: 게시글과 태그 연결
- `post_attached_file`: 게시글과 첨부파일 연결

규칙:

- `post.controller`는 Thymeleaf 모델 준비와 요청 검증만 담당합니다.
- `post.service`는 게시글 작성·수정·복원·발행 요청과 시리즈 생성·수정·게시글 배치를 조합합니다.
- `post.persistence`는 게시글 Entity와 기본 CRUD를 담당합니다.
- 공개 목록·상세의 필터, 정렬, 검색, 태그·카테고리·시리즈 조인은 Querydsl 조회로 작성합니다.
- `/posts`는 카테고리별 공개 글 수, 시리즈 편수·상태(`ACTIVE`: 연재 중, `COMPLETED`: 완결), 실제 `series_order`를 표시합니다. 비공개 시리즈의 메타데이터와 예약 글은 노출하지 않습니다.
- 공통 사이드 메뉴에 글·소개·검색을 배치하고 `/posts`에서만 카테고리·시리즈·태그를 추가합니다. PC는 기본 펼침이며 본문을 옆으로 밀고, 모바일은 기본 접힘인 오버레이로 배경 클릭·Escape로 닫습니다. 메뉴 중앙만 스크롤하고 하단 테마 버튼은 고정합니다. GET 분류 필터를 유지하며 검색은 별도 `/search?q=검색어` 화면에서 제공합니다. 빈 검색어에는 안내만, 검색 시 제목·요약·본문 일치 결과와 편수를 표시합니다. 헤더 왼쪽에는 메뉴 버튼과 로고, 오른쪽에는 사용자 드롭다운을 표시하며 검색과 테마는 사이드 메뉴에서 접근합니다. 사용자 드롭다운은 외부 클릭·Escape·포커스 이동으로 닫힙니다. 인증 HTTP 기능이 아직 없어 로그인은 준비 중인 비활성 항목이며, 사용자 정보·로그아웃·관리자 메뉴는 실제 인증 연동 후 추가합니다. 관리자 입력 화면은 제공하지 않습니다.
- 시리즈는 대표 카테고리가 필수이며, 게시글을 시리즈에 배치하면 게시글 카테고리를 시리즈 대표 카테고리로 맞춥니다.

## 인증·권한 모듈

### `com.deanp.blog.auth`

관리자 로그인과 권한 그룹을 담당합니다. Spring Security 도입 시 이 모듈의 web/service 경계와 연결합니다.

바라보는 테이블:

- `app_user`: 사용자 계정
- `auth_group`: 권한 그룹
- `user_group`: 사용자-그룹 연결
- `group_menu`: 그룹-메뉴 권한 연결

규칙:

- 비밀번호 해시 비교와 계정 상태 검사는 `auth.service`에서 처리합니다.
- 사용자·그룹의 단순 생성·수정·조회는 Repository CRUD를 사용합니다.
- 권한 조합, 메뉴별 권한 조회, 활성 상태 조건은 Querydsl을 사용합니다.

## 메뉴 모듈

### `com.deanp.blog.menu`

관리자 메뉴 트리와 정렬을 담당합니다.

바라보는 테이블:

- `menu`: 메뉴와 부모 메뉴 관계
- `group_menu`: 메뉴별 그룹 권한

`menu.parent_id`를 이용한 계층 조회와 권한별 메뉴 필터는 Querydsl 또는 명시적인 전용 조회로 관리합니다. 단순 메뉴 CRUD는 Spring Data Repository를 사용합니다.

## 공통 코드 모듈

### `com.deanp.blog.code`

상태·유형·이벤트 등의 공통 코드를 관리합니다.

바라보는 테이블:

- `common_code`: 코드 그룹
- `common_code_detail`: 코드 값

코드 그룹별 활성 코드 조회처럼 조건이 단순한 경우 Repository 메서드를 사용하고, 여러 상태·정렬·유효 기간 조건이 추가되면 Querydsl을 사용합니다.

## 태그 모듈

### `com.deanp.blog.tag`

태그 마스터와 게시글 태그 연결을 담당합니다.

바라보는 테이블:

- `tag`: 태그 마스터
- `post_tag`: 게시글-태그 연결

태그명·slug 중복 검사는 Repository 또는 DB UNIQUE 제약을 사용합니다. 게시글 목록에서 여러 태그 조건으로 검색하거나 태그별 게시글 수를 집계하는 조회는 Querydsl을 사용합니다.

## 미디어 모듈

### `com.deanp.blog.media`

첨부파일 메타데이터와 게시글 연결을 담당합니다. 실제 파일 바이트는 외부 저장소에 둡니다.

바라보는 테이블:

- `attached_files`: 파일명·경로·크기·checksum
- `post_attached_file`: 게시글-첨부파일 연결

파일 업로드 메타데이터의 기본 CRUD는 Repository를 사용합니다. 게시글별 노출 파일 조회, 정렬, 연결 상태 검사는 Querydsl을 사용할 수 있습니다.

## 발행 모듈

### `com.deanp.blog.publishing`

Markdown/Git 발행과 콘텐츠 이력을 담당합니다.

바라보는 테이블:

- `post_revision`: 콘텐츠 버전 및 복원 대상
- `publish_history`: Git 발행 처리 결과
- `post_detail`: 발행 대상 게시글

규칙:

- revision 생성과 복원은 `publishing.service`에서 트랜잭션 단위로 조합합니다.
- Git commit, 파일별 처리 상태, 재시도 이력은 `publish_history`로 관리합니다.
- 최근 실패 발행, 재시도 대상, 게시글별 이력 조회는 Querydsl을 사용합니다.
- `board_revision`과 `publish_history`를 하나의 테이블이나 책임으로 합치지 않습니다.

## 방문자 모듈

### `com.deanp.blog.visitor`

익명 방문자와 조회·유입 이벤트 및 통계를 담당합니다.

바라보는 테이블:

- `visitor`: 익명 방문자 식별자
- `visitor_event`: 원본 방문 이벤트
- `visitor_daily_summary`: 일별 집계
- `post_detail`: 게시글별 조회 대상

원본 이벤트 저장은 단순 insert Repository를 사용할 수 있습니다. 기간, 국가, 검색엔진, 게시글별 집계와 유일 방문자 수 조회는 Querydsl 또는 집계 전용 SQL을 사용합니다.

개인정보가 포함될 수 있는 IP·User-Agent·검색어는 보관 기간과 익명화 정책을 먼저 확정합니다.

## 감사 로그 모듈

### `com.deanp.blog.audit`

관리자와 시스템의 주요 변경·접근 이력을 담당합니다.

바라보는 테이블:

- `audit_log`: 행위자·대상·요청·메타데이터·시각
- `app_user`: 감사 행위자 참조

로그 기록은 서비스 또는 이벤트 경계에서 단순 insert로 처리합니다. 관리자별·대상별·기간별 감사 이력 검색은 Querydsl을 사용합니다. 감사 로그는 일반 업무 트랜잭션과 조회 책임을 섞지 않습니다.

## 검색 모듈

### `com.deanp.blog.search`

초기에는 PostgreSQL 기반 게시글 검색을 담당합니다. 검색 전용 테이블은 두지 않습니다.

바라보는 테이블:

- `post_detail`: 제목·요약·본문
- `post_category`: 카테고리 필터
- `post_series`: 시리즈 필터
- `post_tag`: 태그 연결
- `tag`: 태그명

제목·상태·카테고리·태그·발행일을 조합한 검색은 Querydsl을 기본으로 사용합니다. 검색 조건이 PostgreSQL 전문 검색으로 확장되면 Querydsl BooleanExpression과 PostgreSQL 전용 표현식의 책임을 `search.persistence`에 둡니다.

## 테이블-패키지 매핑 요약

| 테이블 | 주 소유 패키지 | 함께 조회하는 패키지 |
|---|---|---|
| `app_user` | `auth` | `audit`, `post`, `publishing` |
| `auth_group` | `auth` | `menu` |
| `user_group` | `auth` | `menu` |
| `menu` | `menu` | `auth` |
| `group_menu` | `auth` | `menu` |
| `common_code` | `code` | 각 업무 모듈 |
| `common_code_detail` | `code` | 각 업무 모듈 |
| `post_detail` | `post` | `search`, `publishing`, `visitor` |
| `post_category` | `post` | `search` |
| `post_series` | `post` | `search` |
| `tag` | `tag` | `post`, `search` |
| `post_tag` | `post` | `tag`, `search` |
| `post_revision` | `publishing` | `post` |
| `attached_files` | `media` | `post` |
| `post_attached_file` | `media` | `post` |
| `publish_history` | `publishing` | `post` |
| `audit_log` | `audit` | `auth`, `post`, `publishing` |
| `visitor` | `visitor` | - |
| `visitor_event` | `visitor` | `post`, `search` |
| `visitor_daily_summary` | `visitor` | `post`, `search` |

## Querydsl 작성 기준

- 단순 PK 조회, 단순 저장·수정·삭제, 단순 존재 여부는 Spring Data Repository 메서드를 사용합니다.
- 두 개 이상의 테이블 조인, 동적 검색 조건, 복합 정렬, 페이지네이션, 그룹 집계는 Querydsl을 기본으로 사용합니다.
- Querydsl 코드는 Controller나 Service에 작성하지 않고 `persistence` 또는 `persistence.query`에 둡니다.
- 조회 결과는 Entity를 그대로 웹에 노출하지 않고 전용 projection 또는 조회 DTO로 반환합니다.
- 기능 간 테이블을 직접 조회하지 말고 해당 테이블의 소유 패키지 Repository 또는 명시된 Querydsl 조회 경계를 사용합니다.
- 실제 조회 요구가 생기기 전까지 Querydsl용 추상화와 공통 검색 프레임워크는 만들지 않습니다.
