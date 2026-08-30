# Architecture

## Status

- 문서 상태: 초안
- 마지막 갱신: 2026-08-30
- 현재 단계: 공개 화면 템플릿 검증

## System Shape

`dean-p-blog`는 Spring Boot 기반 SSR 모놀리스입니다.

```text
Browser
  ↓ HTTP
Spring Boot
  ├── Spring MVC Controller
  ├── Application/Domain logic
  ├── Persistence boundary
  └── Thymeleaf templates + static assets
       ↓
External PostgreSQL
```

- 프론트엔드 전용 React/Next.js/Node.js 서버는 두지 않습니다.
- Spring Boot가 HTML, CSS, JavaScript와 향후 API를 함께 제공합니다.
- Java 25, Spring Boot 4.1.1, Gradle을 사용합니다.

## Current Implementation

- `BlogController`: 홈, 글 목록, 글 본문, 소개 라우팅
- `PostView`: 화면 표시용 게시글 데이터
- `SamplePostRepository`: 화면 검증용 메모리 샘플 데이터
- Thymeleaf: 서버 렌더링 HTML
- Vanilla JavaScript: 라이트/다크 테마 전환
- PostgreSQL: 연결 프로필과 환경변수 설정만 준비됨

현재 게시글 화면은 실제 PostgreSQL이 아니라 샘플 저장소를 사용합니다.

## Content Direction

- Obsidian은 작성 도구로 사용합니다.
- 블로그 전용 Git 저장소를 콘텐츠 저장소로 사용하는 방향입니다.
- 서버가 개인 Vault 전체를 직접 읽지는 않습니다.
- 공개 발행 기준과 Markdown 동기화 방식은 후속 설계 대상입니다.

## Planned Boundaries

- `post`: 게시글, 태그, 시리즈, 발행
- `web`: HTTP 요청과 Thymeleaf 화면 연결
- `persistence`: PostgreSQL 매핑과 조회
- `publishing`: Markdown/Git 기반 발행 파이프라인
- `media`: 애플리케이션 외부 저장소의 이미지·첨부파일
- `search`: 초기에는 PostgreSQL 검색 우선

위 경계는 구현 진행에 따라 조정하며, 필요하지 않은 계층이나 추상화는 추가하지 않습니다.

## Decisions Pending

- 게시글 PostgreSQL 스키마와 마이그레이션 소유 주체
- Markdown front matter의 최종 형식
- Git push 이후 발행 방식
- 관리자 인증 및 권한 모델
- 검색 품질이 부족할 때 외부 검색 엔진을 도입할지 여부
