# 모듈과 패키지 설계

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

- `board`: 게시판(개발 `dev`, 노트 `notes`). 목록 주소는 `/{slug}`
- `post_detail`: 게시글 본문·제목·상태, 속한 게시판
- `post_category`: 대표 카테고리, 속한 게시판
- `post_series`: 시리즈와 대표 카테고리
- `post_revision`: 게시글 수정 버전
- `post_tag`: 게시글과 태그 연결
- `post_attached_file`: 게시글과 첨부파일 연결

규칙:

- `post.controller`는 Thymeleaf 모델 준비와 요청 검증만 담당합니다.
- `post.service`는 게시글 작성·수정·복원·발행 요청과 시리즈 생성·수정·게시글 배치를 조합합니다.
- `post.persistence`는 게시글 Entity와 기본 CRUD를 담당합니다.
- 공개 목록·상세의 필터, 정렬, 검색, 태그·카테고리·시리즈 조인은 Querydsl 조회로 작성합니다.
- 게시판(`board`): 글과 카테고리는 한 게시판에 속하고, 시리즈는 대표 카테고리의 게시판을 따릅니다. 글의 카테고리·시리즈는 글과 같은 게시판이어야 하며(`PostEditorService`·`PostSeriesService`가 검사), 카테고리를 다른 게시판으로 옮기면 그 카테고리의 글도 함께 옮겨지고(`CategoryAdminService`), 시리즈 대표 카테고리를 바꾸면 시리즈 글도 그 카테고리의 게시판으로 옮겨집니다. 글 주소 `/posts/{slug}`는 게시판과 관계없이 전체에서 고유합니다. 정렬 순서가 가장 앞선 활성 게시판(개발)이 기본 게시판으로, 게시판을 고르지 않은 새 글·카테고리가 들어가고 예전 목록 주소 `/posts`는 그 목록으로 301 이동합니다(필터 쿼리 유지).
- 게시판 목록(`/dev`, `/notes`, `BlogController`는 점이 없는 한 단어 경로 `/{boardSlug}`를 받아 활성 게시판이 아니면 404)은 카테고리별 공개 글 수, 시리즈 편수·상태(`ACTIVE`: 연재 중, `COMPLETED`: 완결), 실제 `series_order`를 표시합니다. 비공개 시리즈의 메타데이터와 예약 글은 노출하지 않습니다.
- 공통 사이드 메뉴에 소개와 게시판(개발·노트)을 배치하고, 지금 보는 게시판(그 밖의 화면은 기본 게시판) 아래에만 접고 펼 수 있는 하위 메뉴로 그 게시판의 카테고리·시리즈·태그 필터를 둡니다. 필터는 모든 화면에서 펼칠 수 있으며(`post.controller.SidebarFilterModel`, 게시판 목록·상세가 아닌 화면은 `SidebarFilterInterceptor`가 기본 게시판 기준으로 선택 없이 채움), 게시판 목록과 글 상세(`/posts/{slug}`, 그 글의 게시판 기준)에서는 펼친 채로 그립니다. 하위 메뉴가 없는 항목은 아이콘 자리만큼 여백을 두어 글씨 줄을 맞춥니다. 목록의 글 링크는 선택한 카테고리·시리즈·태그·검색어를 쿼리 파라미터로 넘겨 상세 화면의 사이드 메뉴에서도 같은 선택을 유지합니다. PC는 기본 펼침이며 본문을 옆으로 밀어 메뉴를 제외한 영역의 가운데에 두고, 모바일은 기본 접힘인 오버레이로 배경 클릭·Escape로 닫습니다. 메뉴 중앙만 스크롤하고 하단 테마 버튼은 고정합니다. GET 분류 필터를 유지하며 검색은 별도 `/search?q=검색어` 화면에서 제공합니다. 빈 검색어에는 안내만, 검색 시 제목·요약·본문 일치 결과와 편수를 표시합니다. 없는 주소·글(404)과 처리 오류는 Spring Boot 기본 오류 처리기가 `templates/error.html`을 그려 공통 헤더·사이드 메뉴와 홈·글 목록·검색 링크를 보여 주며, 브라우저가 아닌 요청에는 JSON 오류 본문을 그대로 줍니다. 헤더 왼쪽에는 메뉴 버튼과 로고, 오른쪽에는 사용자 드롭다운을 표시하며 검색과 테마는 사이드 메뉴에서 접근합니다. 사용자 드롭다운은 외부 클릭·Escape·포커스 이동으로 닫힙니다. 로그인하지 않은 사용자에게는 로그인 링크를, 로그인한 사용자에게는 아이디·로그아웃과 권한이 있을 때 관리 대시보드 링크를 표시합니다. 글 관리 권한이 있으면 사이드 메뉴의 소개·글 아래에 ‘관리자’ 항목(누르면 대시보드)과 그 하위 메뉴(대시보드·글·새 글·카테고리·시리즈·태그)를 더하고, 하위 메뉴는 관리 화면에서는 펼치고 그 밖에서는 접어 두며 ‘관리자’ 왼쪽 아이콘으로 접고 펼 수 있게 하고, `auth.controller.CurrentUserAdvice`가 요청 주소로 고른 `adminMenu` 값으로 지금 화면을 표시합니다. 글 관리 화면(`/admin/posts`)은 `post.controller.AdminPostController`가 맡아 초안 포함 목록(제목 검색, `status=draft|published|deleted` 상태 필터), 작성·수정, 발행·비공개 전환, 삭제·복구, 이미지·Archify 다이어그램 업로드, 저장된 내용 미리보기와 본문 실시간 미리보기(`POST /admin/posts/preview-render`), 코드 블록·Mermaid 넣기 도구를 제공합니다. 삭제는 `post_detail.status`를 `DELETED`로 바꾸는 소프트 삭제라 태그·첨부 연결을 그대로 두고 공개 화면과 기본 목록에서만 숨기며, 복구하면 초안으로 돌아갑니다. 글 slug는 삭제하지 않은 글끼리만 고유하므로(V11 부분 고유 인덱스) 삭제한 글의 주소를 새 글이 다시 쓸 수 있고, 그 주소를 다른 글이 쓰고 있으면 복구를 거부합니다. 삭제한 글은 복구 전까지 수정·이미지 업로드를 막습니다. 작성·저장·발행·비공개 전환·삭제·복구할 때마다 같은 트랜잭션에서 제목·요약·본문을 `post_revision`에 버전으로 남기며(공개 상태를 바꾸지 않은 저장은 직전 버전과 같으면 건너뜀), 기록이 없던 글은 처음 바꿀 때 바꾸기 전 상태를 `BASELINE` 버전으로 먼저 남깁니다. 수정 화면에서 이력을 보고 `/admin/posts/{id}/revisions/{no}`에서 버전 내용과 현재와 다른 항목을 확인한 뒤 되돌릴 수 있습니다. 되돌림은 제목·요약·본문만 바꾸고 주소·분류·태그·공개 상태는 유지하며, 그 결과도 새 버전으로 남습니다. 발행 시각(한국 시간, 분 단위)을 지정해 발행할 수 있고, 미래 시각이면 상태는 `PUBLISHED`이지만 공개 조회가 `published_at <= now()`만 보여 주므로 그 시각에 자동으로 공개됩니다(예약 발행, 별도 스케줄러 없음). 관리 화면은 이런 글을 ‘예약’으로 표시하며, 공개 중인 글은 저장만으로 발행 시각을 바꿀 수 있습니다. 시각 없이 다시 발행하면 처음 발행 시각을 유지하되, 남아 있던 값이 미래이면 지금으로 바꿉니다. 관리 화면끼리는 사이드 메뉴의 ‘관리자’ 하위 메뉴로 오갑니다. 카테고리(`/admin/categories`, `post.controller.AdminCategoryController`·`post.service.CategoryAdminService`)는 한 화면에서 추가·이름·주소·정렬 순서 변경·삭제를 하며, 삭제하지 않은 글이나 시리즈가 쓰는 카테고리는 지우지 않고 삭제한 글만 쓰면 그 글의 카테고리를 비운 뒤 지웁니다. 공개 사이드 메뉴의 카테고리도 이 정렬 순서를 따릅니다. 시리즈(`/admin/series`, `AdminSeriesController`·`PostSeriesService`)는 작성·수정·상태(`ACTIVE` 연재 중, `COMPLETED` 완결, `HIDDEN` 숨김)·삭제와 글 넣기·빼기·순서 변경을 제공하며, 삭제하지 않은 글이 속해 있으면 삭제를 거부하고 삭제한 글만 남았으면 연결을 끊고 지웁니다. 태그(`/admin/tags`, `tag.controller.AdminTagController`·`tag.service.TagAdminService`)는 이름·주소 변경, 다른 태그로 병합(대상 태그를 붙이고 원래 태그 삭제), 삭제(글은 유지)를 제공합니다. 카테고리·시리즈·태그 주소는 비우면 이름을 소문자로 바꿔 만들고 글 주소와 같은 금지 문자 규칙(`post.service.Slugs`)을 따릅니다. 저장 규칙은 `post.service.PostEditorService`, 이미지 저장은 `media.service.PostImageService`가 담당합니다.
- 시리즈는 대표 카테고리가 필수이며, 게시글을 시리즈에 배치하면 게시글 카테고리를 시리즈 대표 카테고리로 맞춥니다.

## 인증·권한 모듈

### `com.deanp.blog.auth`

관리자 로그인과 권한 그룹을 담당합니다.

- `auth.controller`: 로그인 화면(`/login`), Spring Security 경로 규칙(`/admin/**`만 `POST_WRITE` 요구, 나머지 공개), 공통 헤더용 로그인 상태 모델(`CurrentUserAdvice`).
- `auth.service`: 그룹 메뉴 권한을 로그인 권한으로 바꾸는 `BlogUserDetailsService`, 환경변수 관리자 계정 준비(`AdminAccountBootstrap`), 아이디별 연속 실패 잠금(`LoginAttemptService`). 운영 설정은 `docs/production.md`의 Admin Account를 따릅니다.
- `auth.persistence.query`: 사용자-그룹-메뉴 권한 조인(`GroupPermissionQueryRepository`).

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

첨부파일 메타데이터와 게시글 연결을 담당합니다. 실제 파일 바이트는 외부 저장소의 `posts/{게시글 ID}/{파일명}`에 둡니다. `media.controller.PublicMediaConfiguration`은 `/media/posts/**`만 해당 `posts/` 디렉터리에서 제공합니다. Archify 다이어그램 HTML(`media.service.PostDiagramService`)은 `<meta name="generator" content="archify …">`가 있는 UTF-8 `.html`(5MB 이하)만 받아 `diagrams/{게시글 ID}/{파일 ID}.html`에 두고 `attached_files`에 `text/html`로 기록합니다(스키마 변경 없음). 스크립트를 담은 파일이므로 정적 경로로 공개하지 않고, `media.controller.DiagramController`가 `/media/diagrams/{id}`에서 CSP `sandbox allow-scripts allow-downloads allow-popups`(같은 출처 권한 없음, 네트워크·폼 차단)와 `X-Frame-Options: SAMEORIGIN`을 붙여 파일을 흘려보냅니다(업로드마다 새 ID라 1년 `immutable` 캐시, 체크섬 ETag로 304). 생성기 표시 확인은 잘못된 파일을 막는 용도이며 격리는 CSP sandbox가 맡습니다. 나머지 응답(오류 화면·정적 파일 포함)은 컨트롤러가 정하지 않은 한 `X-Frame-Options: DENY`입니다. 이미지와 마찬가지로 다이어그램도 글의 공개 여부와 관계없이 ID로 열립니다. 이미지 목록과 대표 이미지 후보에서는 다이어그램을 뺍니다.

첨부 파일(`media.service.PostFileService`)은 확장자 허용 목록(PDF, zip·7z·gz·tar, docx·xlsx·pptx·hwp·hwpx, txt·log·md·csv·json, 소스 코드)에 드는 20MB 이하 파일만 받아 `files/{게시글 ID}/{파일 ID}.{확장자}`에 두고, 브라우저가 보낸 타입 대신 확장자로 정한 MIME 타입(소스 코드는 `text/plain`)과 SHA-256 체크섬을 `attached_files`에 기록합니다(스키마 변경 없음). 요청 크기 한도(`spring.servlet.multipart`)는 21MB이고, 이미지(10MB)·다이어그램(5MB)은 각자 한도를 따로 확인합니다. `media.controller.FileController`는 `/media/files/{id}`에서 `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, CSP `sandbox; default-src 'none'`를 붙여 다운로드로만 내보냅니다. 공개 글(`PUBLISHED`이고 발행 시각이 지남)의 파일은 누구나, 초안·예약·삭제한 글의 파일은 로그인한 작성자(`post_detail.author_id`)만 받으며, 그 밖에는 있는지 알리지 않도록 404로 답합니다. 공개 상태가 바뀔 수 있으므로 `private, no-cache`와 체크섬 ETag로 매번 다시 확인합니다. 글 수정 화면에서 첨부 파일을 지우면 연결·메타데이터를 지우고 커밋 뒤 파일을 지웁니다. 본문 링크는 그대로 남습니다. 공개 글 화면은 본문 아래에 첨부 파일 목록(이름·크기)을 붙입니다.

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

- revision 생성과 복원은 `publishing.service`에서 트랜잭션 단위로 조합합니다. `PostRevisionService`가 버전 기록·이력 조회·되돌림을 맡고, `post.service.PostEditorService`는 글을 바꾸는 트랜잭션 안에서 이를 호출합니다. 변경 종류(`change_type`)는 `RevisionChangeType` 이름(`BASELINE`, `CREATE`, `UPDATE`, `PUBLISH`, `UNPUBLISH`, `DELETE`, `RESTORE`, `REVERT`)으로 저장합니다.
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

관리 대시보드(`/admin`, `post.controller.AdminDashboardController`)는 `visitor.service.VisitorStatsService`로 고른 기간의 조회 통계(오늘·최근 7일·조회 기간 합계, 조회수 막대와 방문자 꺾은선을 겹친 추이, 삭제한 글을 뺀 많이 본 글, Referer 호스트별 유입 경로, 검색 엔진별 검색 유입, 접속 국가)를, `PostEditorService.summarize()`로 상태별 글 수와 예약·최근 고친 글을 모읍니다. 통계는 일별 요약(UTC 날짜) 대신 `visitor_event`를 한국 시간 날짜로 묶어 세며, 블로그 자신의 호스트에서 온 조회는 ‘블로그 안 이동’으로 표시합니다. 조회 기간은 기간 버튼(`period=7d|30d|90d|6m|1y`, 기본 30일, 오늘로 끝남)이나 시작일·종료일(`from`·`to`, 오늘 이전, 최대 366일)로 고르며, 잘못된 값이면 최근 30일과 안내 문구를 보여 줍니다. 92일 이하는 하루씩, 넘으면 월요일에 시작하는 주 단위로 묶어 그립니다. 로그인 후 첫 화면이 대시보드입니다.

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

## 검색엔진 노출 모듈

### `com.deanp.blog.seo`

- `seo.controller.SearchEngineController`: `/robots.txt`와 `/sitemap.xml`을 응답합니다. robots.txt는 `/admin`·`/login`·`/search`만 막고 사이트맵 위치를 알립니다. 사이트맵에는 홈·게시판 목록(`/dev`, `/notes`)·소개와, 공개 화면과 같은 기준(`PUBLISHED`이고 `published_at <= now()`)의 글 주소(`PostDetailRepository.findPublishedPostLinks`)를 수정일과 함께 넣습니다.
- `seo.controller.SearchMetadataAdvice`: 공통 head가 쓰는 대표 주소(쿼리를 뺀 경로), 기본 설명, 공유 미리보기 종류, 색인 제외 여부를 모든 화면 모델에 담습니다. 관리·로그인·검색·오류 화면과 조건이 붙은 게시판 목록(목록 컨트롤러가 표시)은 `noindex, follow`로 표시합니다. 글 상세는 `BlogController`가 글 요약·`article`·대표 이미지로 덮어씁니다.
- 사이트 주소는 설정하지 않고 요청에서 만듭니다(`server.forward-headers-strategy: native`로 프록시의 호스트·https를 반영).
- `app.seo.indexable=false`(dev 프로필)이면 robots.txt가 전체를 막고 모든 화면을 `noindex`로 표시하며 사이트맵은 404입니다. 개발 서버가 검색 결과에 나오지 않게 하기 위함입니다.

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
| `board` | `post` | `seo` |
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
