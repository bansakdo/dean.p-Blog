# Architecture

## Status

- 문서 상태: 진행 중
- 마지막 갱신: 2026-09-19
- 현재 단계: 공개 게시글 화면이 PostgreSQL의 발행 콘텐츠를 Querydsl로 조회하고 카테고리·시리즈·태그·검색 필터를 제공

## System Shape

`dean-p-blog`는 Spring Boot 기반 SSR 모놀리스입니다.

```text
Browser
  ↓ HTTP
Spring Boot
  ├── Feature Controller
  ├── Feature Service
  ├── Persistence Entity / Repository
  ├── Querydsl 또는 필요한 Native SQL 조회
  └── Thymeleaf templates + static assets
       ↓
External PostgreSQL
```

- 프론트엔드 전용 React/Next.js/Node.js 서버는 두지 않습니다.
- Spring Boot가 HTML, CSS, JavaScript와 향후 API를 함께 제공합니다.
- Java 25, Spring Boot 4.1.1, Gradle을 사용합니다.

## Default MVC Pattern

기본 애플리케이션 구조는 Spring MVC의 다음 책임 분리를 따릅니다.

```text
Controller → Service → Repository
```

- `Controller`: HTTP 요청, 입력 검증 경계, 화면 모델 준비, 응답 선택
- `Service`: 유스케이스 조합과 여러 경계에 걸친 업무 로직
- `Repository`: PostgreSQL 조회와 저장 등 영속성 접근
- 단순한 전달 역할만 하는 Service는 만들지 않습니다.

기능별 기본 패키지는 다음과 같습니다.

```text
com.deanp.blog.<feature>
├── controller
├── service
└── persistence
    ├── entity
    ├── repository
    └── query
```

- `controller`: 해당 기능의 HTTP 요청과 화면/API 응답
- `service`: 해당 기능의 유스케이스와 업무 흐름
- `persistence.entity`: PostgreSQL 테이블 Entity와 FK 연관관계
- `persistence.repository`: 단순 CRUD용 Spring Data Repository
- `persistence.query`: Querydsl 기본 조회와 필요한 Native SQL 조회

일반적인 복합 조회는 Querydsl을 사용합니다. `WITH`, `WITH RECURSIVE`, 윈도우 함수, PostgreSQL 전용 기능처럼 Querydsl JPA로 표현하기 어렵거나 SQL이 더 명확한 경우에는 같은 Custom Query Repository 경계에서 parameterized Native SQL을 사용합니다.

## Current Implementation

- `post.controller.BlogController`: 홈, 글 목록, 글 본문, 소개 라우팅
- `post.service.PostService`: 공개 발행 게시글을 화면용 데이터로 변환하고 Markdown을 HTML로 렌더링
- `PostView`: 템플릿에 노출하는 게시글 표시용 projection
- `post.persistence.repository.PostDetailRepository`: 단순 CRUD와 공개 게시글 Custom Querydsl 조회 경계
- `post.service.PostSeriesService`: 시리즈 생성·수정과 게시글 배치를 조합하며, 시리즈 대표 카테고리를 연결 게시글 카테고리에 동기화
- `post.persistence.query.PublicPostQueryRepository`: `PUBLISHED` 상태와 `published_at IS NOT NULL` 조건, 최신순 목록, slug 조회, 카테고리·시리즈·태그·검색 필터와 태그 조인 결과를 제공
- 게시판 상위 테이블 없이 `post_detail`을 실제 글로 사용하는 기능별 JPA Entity와 Spring Data Repository
- Thymeleaf: 서버 렌더링 HTML
- `media.controller.PublicMediaConfiguration`: 외부 저장소의 `posts/` 파일만 `/media/posts/**`로 공개
- Vanilla JavaScript: 라이트/다크 테마 전환
- PostgreSQL: Flyway가 `blog` 스키마 evolution을 소유하고 Hibernate는 `validate`로 매핑 호환성만 확인

현재 게시글 화면은 샘플 fallback 없이 PostgreSQL `blog.post_detail`의 공개 발행 콘텐츠만 사용합니다. Markdown 원문은 서버에서 HTML로 변환하며, 템플릿에는 Entity가 아니라 `PostView`만 전달합니다. V8에서 추가한 시리즈와 기존 글은 유지하며, V10은 게시판 상위 테이블 `post`와 `post_id` 참조만 제거합니다. 카테고리·시리즈 slug는 전체에서 고유합니다.

## Content Direction

- Obsidian은 작성 도구로 사용합니다.
- 블로그 전용 Git 저장소를 콘텐츠 저장소로 사용하는 방향입니다.
- 서버가 개인 Vault 전체를 직접 읽지는 않습니다.
- 공개 표시 기준은 `post_detail.status = PUBLISHED`이고 `published_at`이 있는 글입니다.
- Markdown 동기화·발행 파이프라인은 후속 설계 대상입니다.

## Planned Boundaries

- `post`: 게시글, 태그, 시리즈, 발행
- 각 기능의 `controller`: HTTP 요청과 Thymeleaf 화면/API 연결
- 각 기능의 `service`: 유스케이스와 업무 흐름
- 각 기능의 `persistence`: PostgreSQL 매핑과 CRUD·복합 조회
- `publishing`: Markdown/Git 기반 발행 파이프라인
- `media`: 애플리케이션 외부 저장소의 이미지·첨부파일
- `search`: 초기에는 PostgreSQL 검색 우선

최상위 `web` 패키지는 전역 예외 처리나 공통 MVC 설정처럼 기능에 속하지 않는 웹 인프라가 필요할 때만 사용합니다. 위 경계는 구현 진행에 따라 조정하며, 필요하지 않은 계층이나 추상화는 추가하지 않습니다.

## Decisions Pending

- Markdown front matter의 최종 형식
- Git push 이후 발행 방식
- 관리자 인증 및 권한 모델
- 검색 품질이 부족할 때 외부 검색 엔진을 도입할지 여부
