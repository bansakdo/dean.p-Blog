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

## CI database tests

로컬 `./gradlew test`는 기존처럼 Testcontainers의 일회용 PostgreSQL을 사용합니다. Jenkinsfile은 외부 CI 전용 DB 작업 `./gradlew --no-daemon --max-workers=1 ciDbIntegrationTest bootJar`를 호출합니다. Jenkins 작업 매개변수로 `CI_DB_URL`을 주입하고, 별도 Credential ID `dean-p-blog-ci-db`로 `CI_DB_USER`, `CI_DB_PASSWORD`를 바인딩합니다. 암호를 소스·로그·명령 인수로 전달하지 마세요.

CI 작업은 별도 JVM에서 접속 사전 검증 → V3~V10 드리프트 재현 테스트(테스트마다 `blog` 스키마 clean) → `blog` 스키마 clean 및 최신 마이그레이션 적용 → 스키마 검증 → 일반 테스트 순으로 진행합니다. `CI_DB_ALLOWED_HOST`, `CI_DB_ALLOWED_PORT`는 허용할 접속 대상(URL과 정확히 일치해야 함), `CI_DB_SERVER_ADDR`, `CI_DB_SERVER_PORT`는 독립적으로 확인한 PostgreSQL 서버의 `inet_server_addr()`·`inet_server_port()` 기대값으로 Jenkins의 비밀 아닌 환경설정에 각각 주입합니다. 모두 필수이며 기본값은 없습니다. NAT가 있으면 외부 접속 주소·포트와 실제 서버가 보고하는 내부 주소·포트가 다를 수 있으므로 후자를 복사해 외부값으로 가정하지 마세요. 기대값을 확인할 수 없으면 CI 작업을 실행하지 않습니다.

사전 검증은 접속 URL·계정과 실제 연결의 `current_database()`(`dean_p_blog_ci`), `current_user`(`dean_p_blog_app_ci`), 서버 주소·포트, 계정 관리자 권한을 확인합니다. URL과 허용 대상을 동일한 잘못된 값으로 설정하면 두 입력의 일치만으로 안전을 보증하지 못합니다. 허용 대상과 서버 기대값은 서로 다른 근거로 확인하고, DB명·계정·권한 검사를 추가 방어선으로 유지합니다. 계정의 다른 DB 접속 권한 제한도 별도로 유지해야 합니다.

이 DB는 테스트가 스키마 전체를 지우므로 다른 작업과 공유하지 마세요. Jenkins는 해당 DB 사용 작업을 직렬 실행하며 기존 `clean test bootJar` 대신 CI 전용 작업을 호출합니다. CI 전용 DB 외에서는 이 작업을 실행하지 마세요. Jenkins에서 CI 단계는 확인했지만 운영 DB 및 운영 배포는 별도 검증 대상입니다.

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

## PostgreSQL backup preparation

Synology의 운영 PostgreSQL 컨테이너 이름은 `postgres`, 서버 메이저 버전은 18입니다. `scripts/postgresql/backup-production.sh`는 서버 컨테이너의 현재 이미지로 일회성 **클라이언트**를 실행해 `dean_p_blog` 데이터베이스 전체를 커스텀 형식으로 저장합니다. 새 PostgreSQL 서버 컨테이너를 만들지 않으며, 스크립트 실행만으로 예약 작업이나 자동 배포가 활성화되지는 않습니다.

- 백업 역할 `dean_p_blog_app_backup`에는 LOGIN·운영 DB CONNECT와 필요한 스키마 USAGE/테이블·시퀀스 SELECT만 부여합니다. 관리자·쓰기 권한은 부여하지 않습니다. 새 객체에도 권한이 이어지도록 객체 소유자별 기본 권한을 점검합니다.
- NAS의 `/volume1/Backup/PostgreSQL/Database/dean_p_blog`는 미리 만들고 접근을 제한합니다. 실제 데이터 볼륨과 분리하되 NAS 내부 사본만으로는 NAS 장애에 대비할 수 없으므로 다른 장비에 암호화된 사본을 추가해야 합니다.
- 별도 보호 경로에 libpq `.pgpass` 파일을 만들고 0600(또는 0400) 권한을 부여합니다. 내용 형식은 `127.0.0.1:5432:dean_p_blog:dean_p_blog_app_backup:<비밀번호>`입니다. 비밀번호에 `:`나 `\`가 있으면 libpq 형식대로 `\`로 이스케이프합니다. 암호는 Git, 예약 명령, 로그, 백업 디렉터리에 두지 않습니다.
- NAS에서 스크립트를 복사한 위치에서 `BACKUP_DIR=/volume1/Backup/PostgreSQL/Database/dean_p_blog BACKUP_PGPASS_FILE=<보호된 .pgpass 절대경로> sh backup-production.sh`로 우선 1회만 실행합니다. `BACKUP_OK`와 아카이브 파일을 확인하되 이것만으로 복구 가능성을 단정하지 않습니다.
- 운영·개발·CI DB가 아닌 별도 빈 복구 시험 DB에 `pg_restore --no-owner --no-acl`로 실제 복원하고 데이터·`blog.flyway_schema_history`를 확인한 뒤 Synology 예약 작업과 실패 알림, 보관 정책을 구성합니다. 복구 대상 식별과 관리자 권한이 확인되기 전에는 복원 명령을 실행하지 않습니다.
- `pg_dump` 파일에는 PostgreSQL 글로벌 역할 설정이 포함되지 않습니다. 전체 서버 복구를 위해 역할/권한 복구 방법도 별도로 문서화하고 검증해야 합니다. 미디어 호스트 디렉터리도 별도 백업 대상입니다. 복구 시험과 배포 전 백업 연동이 끝날 때까지 운영 자동 배포 게이트를 유지합니다.

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
