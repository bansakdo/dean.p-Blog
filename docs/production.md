# Production

## Status

- 문서 상태: 초안
- 마지막 갱신: 2026-08-31
- 현재 배포 상태: 실제 운영 배포 전

## Runtime

- Java 25 LTS
- Spring Boot 4.1.1
- 외부 PostgreSQL 서버
- 애플리케이션은 Docker 기반 이식성을 고려하되, PostgreSQL을 로컬 컨테이너로 실행하지 않습니다.

## Profiles

- 기본 프로필: 화면 템플릿 검증용. DataSource 자동 설정을 제외합니다.
- `dev`: 개발용 외부 PostgreSQL 연결
- `prod`: 운영용 외부 PostgreSQL 연결

`dev`와 `prod`의 Hibernate `ddl-auto`는 `validate`입니다. Flyway가 `src/main/resources/db/migration`의 SQL migration으로 스키마 생성을 소유하고, Hibernate는 애플리케이션 시작 시 Entity 매핑과 실제 스키마의 호환성만 검증합니다.

실행 예시:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

## Database Configuration

DB 접속 정보는 환경변수로만 주입합니다.

```text
DB_URL
DB_USER
DB_PASSWORD
```

- `.env.dev`와 `.env.prod`는 Git에 커밋하지 않습니다.
- 비밀번호와 토큰을 문서, 로그, 저장소에 기록하지 않습니다.
- 실제 외부 DB 연결 상태와 권한은 운영 환경에서 별도로 확인해야 합니다.

## Database Bootstrap

초기 PostgreSQL 사용자·데이터베이스·스키마 설정 스크립트:

```text
scripts/postgresql/bootstrap.sql
```

애플리케이션 초기 테이블 migration:

```text
src/main/resources/db/migration/V1__create_blog_schema.sql
```

현재 기본 방향:

- 데이터베이스: `dean_p_blog`
- 애플리케이션 사용자: `dean_p_blog_app`
- 운영 프로필 포트: `63050`
- 개발 프로필 포트: `63051`
- 애플리케이션 스키마: `blog`
- PostgreSQL 기본 `public` 스키마는 별도로 존재할 수 있습니다.

스키마 evolution은 Flyway migration으로만 수행합니다. 운영과 개발 프로필 모두 Hibernate 자동 DDL 변경을 사용하지 않습니다.

- Flyway 적용 전 `visitor_daily_summary`에서 동일 `summary_date`·`post_detail_id` 중복 행을 점검하고 백업합니다.
- V5는 중복 데이터가 있으면 unique index 생성에 실패하므로 원인과 보존 절차를 확인한 뒤 별도로 정리해야 합니다.
- V6는 기존 `visitor_event`의 `POST_VIEW` 원본을 UTC 날짜와 게시글별로 집계해 `landing_count`, `view_count`, `unique_visitor_count`를 보정하고, summary 행이 없는 그룹은 새로 생성합니다.
- V6 적용 후 원본 이벤트 수와 summary를 대조하고, Flyway history와 Hibernate validate 결과를 확인합니다.
- V9는 과거 개발 스키마에서 문자 컬럼이 일괄 `varchar(255)`로 생성된 드리프트만 V1-V8 migration의 명시 타입으로 복구합니다. 축소되는 컬럼은 `char_length` 최대값을 먼저 검사하며, 실패 오류에는 테이블·컬럼·최대 길이만 포함하고 실제 값은 노출하지 않습니다.
- V9 적용 전 운영 백업을 확보하고 Flyway validation을 통과시켜야 합니다. V6 checksum repair는 별도 운영 절차이며 애플리케이션이나 migration에서 validation을 자동 우회하지 않습니다.
- V10은 상위 게시판 `post`를 제거합니다. 적용 전 스키마 백업과 카테고리·시리즈 slug 중복 여부를 확인해야 하며, 중복 시 데이터 변경 없이 중단합니다. `post_detail`·카테고리·시리즈·태그·방문 이력은 보존합니다. 기존 애플리케이션은 V10 스키마와 호환되지 않으므로 적용과 코드 교체를 함께 계획해야 합니다.

## Visitor Tracking

- `prod`에서는 `app.visitor.cookie-secure=true`로 HTTPS 쿠키를 사용합니다.
- `X-Forwarded-For`는 `VISITOR_TRUSTED_PROXIES`에 설정한 reverse proxy 주소/CIDR에서 온 요청에만 신뢰하며, 오른쪽부터 확인한 첫 번째 비신뢰 주소를 클라이언트로 사용합니다. 잘못된 체인은 remote address로 대체하고, 기본값은 빈 값입니다.
- reverse proxy는 외부 요청의 기존 `X-Forwarded-For` 값을 제거·재작성해야 합니다.
- 예: `VISITOR_TRUSTED_PROXIES=10.0.0.0/8,192.0.2.10/32`
- 실제 프록시 네트워크가 확정되지 않은 상태에서 해당 환경변수를 설정하지 않습니다.

## Deployment Checklist

- [ ] Java 25 런타임 확인
- [ ] `prod` 프로필 확인
- [ ] `DB_URL`, `DB_USER`, `DB_PASSWORD`를 안전하게 주입
- [ ] 외부 PostgreSQL 연결 및 권한 확인
- [ ] Flyway migration 적용 및 Hibernate validate 시작 확인
- [ ] 애플리케이션 기동 확인
- [ ] HTTPS 인증서와 리버스 프록시 설정
- [ ] 로그 위치와 보존 정책 확인
- [ ] 백업 실행 확인
- [ ] 백업에서 실제 복구 테스트
- [ ] RSS, 사이트맵, robots.txt, 검색 메타데이터 확인
- [ ] 운영 장애 시 롤백 절차 확인

## Not Yet Verified

- 실제 운영 서버 배포
- 실제 운영 서버의 외부 PostgreSQL 접속
- 실제 운영 서버의 Flyway migration 적용
- HTTPS
- 백업·복구
- 모니터링과 알림
