# 방문자 추적 및 게시글 조회수 계획

## 문서 상태

- 상태: 단계별 구현 진행 중
- 범위: 공개 게시글 상세 조회의 익명 방문 추적
- 기준: 서버 사이드 렌더링 Spring MVC + Thymeleaf, PostgreSQL
- 원칙: 방문자의 개인정보를 최소화하고, 원본 이벤트와 집계 데이터를 분리한다.

## 현재 확인된 상태

- `visitor`, `visitor_event`, `visitor_daily_summary` 테이블이 존재한다.
- 관련 JPA 엔티티와 Repository를 사용하는 게시글 조회 이벤트 저장 서비스가 있다.
- 게시글 상세 조회는 `BlogController`에서 공개 게시글을 조회하고 화면을 렌더링한다.
- 게시글 자체에는 조회수 누적 컬럼이 없다.

## 처리 정책

- 익명 방문자 식별자는 쿠키 `deanp_visitor_id`를 사용한다.
- 쿠키 값은 UUID 형식으로 생성한다.
- 쿠키가 없거나 UUID 형식이 잘못되면 새 식별자를 발급한다.
- 쿠키에는 `HttpOnly`, `SameSite=Lax`를 적용한다.
- 쿠키에는 1년 `Max-Age`, `HttpOnly`, `SameSite=Lax`를 적용한다.
- `Secure` 속성은 `app.visitor.cookie-secure` 설정으로 제어하며 개발 프로필은 `false`, 운영 프로필은 `true`를 사용한다.
- 게시글 상세 조회가 정상적으로 공개 글을 찾은 경우에만 방문 이벤트를 기록한다.
- 이벤트 원본은 정상 상세 조회 요청마다 `visitor_event`에 저장하고, 일별 지표는 `visitor_daily_summary`에 집계한다.
- 이벤트 유형은 `POST_VIEW`로 통일한다.
- 현재 일별 집계 범위는 UTC 기준 `summary_date`와 `post_detail_id` 조합이다.
- 조회수는 게시글 상세 조회 이벤트 수로 정의하며 같은 날짜와 게시글의 `view_count`를 매 `POST_VIEW`마다 1 증가시킨다.
- 고유 방문자 수는 같은 날짜와 게시글의 `visitor_event`에 있는 distinct `visitor_id` 수로 유지해 같은 방문자의 반복 조회를 중복 증가시키지 않는다.
- `landing_count`는 이번 단계에서 별도 랜딩 이벤트를 두지 않고 상세 진입 `POST_VIEW`와 동일하게 매 요청 1 증가시킨다.
- 알려진 봇·크롤러·모니터링 User-Agent와 `/health`, `/actuator/health` 요청은 방문자 쿠키 발급과 `visitor`, `visitor_event`, `visitor_daily_summary` 저장에서 제외한다.
- 정적 리소스는 게시글 상세 컨트롤러 경로에 도달하지 않아 방문 추적 대상이 아니다.
- IP 주소는 원문 대신 IPv4 `/24`, IPv6 `/64` 네트워크 주소로 익명화하고, 해석할 수 없는 IP는 저장하지 않는다.
- `X-Forwarded-For`는 요청의 remote address가 `app.visitor.trusted-proxies`에 설정된 주소/CIDR일 때만 첫 주소를 클라이언트 IP로 사용한다. 기본 trusted proxy 목록은 비어 있어 외부 클라이언트가 전달한 헤더를 신뢰하지 않는다.
- UUID 쿠키는 표준 canonical 형식만 기존 식별자로 인정하고, 대문자 입력은 소문자 canonical 값으로 정규화한다.
- Referer는 URL 전체가 아니라 host만 저장하고, User-Agent와 request ID의 기존 길이 제한 정책을 유지한다.
- PostgreSQL transaction advisory lock으로 방문자 식별자와 게시글·UTC 날짜 집계 갱신 순서를 직렬화한다.
- 방문자 저장 실패는 best-effort 경계에서 격리해 게시글 본문 응답에 전파하지 않는다.

## 단계별 진행 계획

### 1단계 — 익명 방문자 쿠키 식별

상태: 완료

목표: 상세 페이지 요청에서 방문자를 안정적으로 식별한다.

작업:

- 쿠키 조회 및 UUID 검증 컴포넌트 구현
- 쿠키가 없거나 잘못된 경우 새 UUID 발급
- 보안 속성이 적용된 쿠키 응답 설정
- 상세 조회 경계에서 식별자 생성·재사용 확인
- 쿠키 처리 단위 테스트와 MVC 테스트 추가

완료 기준:

- 최초 요청은 새 쿠키를 반환한다.
- 동일 쿠키 요청은 같은 방문자 식별자를 사용한다.
- 잘못된 쿠키는 새 값으로 교체한다.
- 아직 DB 이벤트나 조회수 집계는 수행하지 않는다.

### 2단계 — 방문자 및 조회 이벤트 저장

상태: 완료

목표: 정상적인 게시글 상세 조회를 `visitor`와 `visitor_event`에 기록한다.

작업:

- 방문자 조회·생성 및 `last_seen_at` 갱신
- `POST_VIEW` 이벤트 저장
- 게시글 slug와 `post_detail_id` 연결
- IP, User-Agent, Referer, request ID 수집
- 방문 기록 실패가 본문 조회를 중단하지 않도록 장애 경계 정의

완료 기준:

- 신규 방문자는 `visitor`에 한 번만 생성된다.
- 상세 조회마다 유효한 이벤트가 생성된다.
- 존재하지 않는 글은 이벤트를 생성하지 않는다.

### 3단계 — 일별 조회수 및 고유 방문자 집계

상태: 완료

목표: 원본 이벤트를 일별 통계로 집계한다.

작업:

- `visitor_daily_summary` upsert 구현 완료
- 게시글·날짜 기준 집계 범위 확정 완료
- `view_count`, `landing_count`, `unique_visitor_count` 계산 완료
- 동시 요청 시 중복 집계 방지 unique index와 PostgreSQL `INSERT ... ON CONFLICT` upsert 적용 완료
- PostgreSQL transaction advisory lock으로 동시 요청의 이벤트 저장과 summary 재계산 순서 직렬화 완료
- V6 migration으로 기존 `visitor_event` 원본 기준 집계값 보정 완료

완료 기준:

- 같은 날의 조회수가 누적된다.
- 같은 방문자의 반복 조회는 고유 방문자 수를 중복 증가시키지 않는다.
- 원본 이벤트와 집계 결과를 대조할 수 있다.
- `landing_count`는 `POST_VIEW` 상세 진입 수와 동일하게 증가한다.

### 4단계 — 요청 메타데이터 및 필터링

상태: 완료

목표: 분석에 필요한 메타데이터를 안전하게 수집하고 불필요한 요청을 제외한다.

작업:

- Referer host 저장 정책 적용 완료
- User-Agent 기반 봇·크롤러·모니터링 요청 제외 완료
- 헬스체크 URI 제외 완료
- 정적 리소스가 상세 컨트롤러 추적 경로에 도달하지 않음을 MVC 테스트로 확인 완료
- IPv4 `/24`, IPv6 `/64` IP 익명화 적용 완료
- 잘못된 IP 주소 null 저장 적용 완료
- trusted proxy에서만 `X-Forwarded-For` 사용하도록 제한 완료
- 표준 UUID canonical 검증 및 대문자 입력 정규화 완료
- 쿠키 1년 만료, `HttpOnly`, `SameSite=Lax`, 프로필 기반 `Secure` 설정 적용 완료

완료 기준:

- 정상 사용자와 자동화 요청의 처리 기준이 테스트로 고정된다.
- 봇·헬스체크 요청은 방문자 쿠키를 발급하지 않고 방문자·이벤트·요약을 저장하지 않는다.
- 민감한 Referer 경로·쿼리와 원본 IP가 저장되지 않는다.

### 5단계 — 관리자 통계 조회

목표: 관리자만 방문 통계를 조회할 수 있게 한다.

작업:

- 게시글별 조회수·고유 방문자 수 조회
- 기간별 통계 조회
- 관리자 권한 검증
- 통계 화면과 API 테스트

완료 기준:

- 비관리자는 통계에 접근할 수 없다.
- 통계 수치가 원본 이벤트 및 일별 집계와 일치한다.

## 검증 순서

각 단계마다 다음 순서로 검증한다.

1. 단계 전용 단위 테스트
2. Spring MVC 또는 PostgreSQL 통합 테스트
3. 관련 전체 테스트
4. 실제 개발 환경 요청 확인
5. `git diff --check` 및 변경 범위 리뷰

## 미결정 사항

- 쿠키 동의가 필요한 운영 지역 및 법적 기준
- 일별 집계 차원에 국가·검색 엔진을 포함할지
- 관리자 통계의 조회 기간과 보존 기간
