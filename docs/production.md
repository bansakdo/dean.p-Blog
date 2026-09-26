# Production

## Status

- 문서 상태: 운영 배포 기록
- 마지막 갱신: 2026-09-26
- 현재 배포 상태: Mac mini Docker에서 운영 중. LAN 접속 주소는 `http://192.168.1.55:63050/`이며 인터넷 접속은 미검증, HTTPS·도메인은 별도 구성입니다.

## Runtime

- Java 25 LTS
- Spring Boot 4.1.1
- 외부 PostgreSQL 서버
- 루트 `Dockerfile`은 Java 25로 `bootJar`를 빌드하고, 비루트 사용자로 실행하는 Java 25 런타임 이미지를 만듭니다. PostgreSQL 컨테이너는 포함하지 않습니다.
- `docker build -t dean-p-blog:local .`로 이미지만 빌드합니다. `.dockerignore`가 Git·빌드 산출물·`.env*`를 빌드 컨텍스트에서 제외합니다.
- 이미지 내부 HTTP 포트는 `8080`(`WAS_PORT`)이며 Mac mini의 `/Users/macmini/services/dean-p-blog/compose.yaml`은 `192.168.1.55:63050:8080`으로 바인딩합니다. 비밀 파일은 소스·이미지 밖의 `secrets/.env.prod`(0600), 미디어는 `media/` 외부 바인드 마운트에 둡니다. IP가 바뀌면 Compose와 호스트의 `deploy.py` 기동 검사 주소를 함께 변경해야 합니다.
- `main` Jenkins 빌드는 CI 검증 후 제한된 SSH 키로 Mac mini의 `deploy.py`에 커밋 SHA만 전달합니다. 스크립트는 현재 `main` SHA와 운영 DB migration 버전 집합이 소스와 같은지 확인한 뒤 이미지를 빌드·교체하고 `192.168.1.55:63050`의 HTTP 응답을 검사합니다. 실패 시 이전 이미지로 돌아가며, 최초 실패 시 새 컨테이너만 제거합니다. **DB migration 및 데이터는 이미지 롤백으로 되돌아가지 않습니다.**
- Jenkins 에이전트에 Docker socket을 연결하지 않습니다. Jenkins #10에서 CI 테스트 100개와 첫 배포에 성공했고, SCM 폴링 트리거는 등록됐습니다. 이후 변경의 자동 배포, 실제 롤백, 운영 DB 별도 복원, 재부팅 후 동작은 아직 검증하지 않았습니다. 도메인·프록시·Prometheus/Grafana는 미구성입니다. Dockerfile 자체는 배포 스크립트가 아닙니다.

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

로컬 `./gradlew test`는 기존처럼 Testcontainers의 일회용 PostgreSQL을 사용합니다. Jenkinsfile은 외부 CI 전용 DB 작업 `./gradlew --no-daemon --max-workers=1 ciDbIntegrationTest bootJar`를 호출합니다. Jenkins 작업 매개변수로 CI DB 접속 주소·서버 기대값을 주입하고, 별도 Credential ID `dean-p-blog-ci-db`로 `CI_DB_USER`, `CI_DB_PASSWORD`를 바인딩합니다. 암호를 소스·로그·명령 인수로 전달하지 마세요.

CI 작업은 별도 JVM에서 접속 사전 검증 → V3~V10 드리프트 재현 테스트(테스트마다 `blog` 스키마 clean) → `blog` 스키마 clean 및 최신 마이그레이션 적용 → 스키마 검증 → 일반 테스트 순으로 진행합니다. `CI_DB_ALLOWED_HOST`, `CI_DB_ALLOWED_PORT`는 허용할 접속 대상(URL과 정확히 일치해야 함), `CI_DB_SERVER_ADDR`, `CI_DB_SERVER_PORT`는 독립적으로 확인한 PostgreSQL 서버의 `inet_server_addr()`·`inet_server_port()` 기대값으로 Jenkins의 비밀 아닌 환경설정에 각각 주입합니다. 모두 필수이며 기본값은 없습니다. NAT가 있으면 외부 접속 주소·포트와 실제 서버가 보고하는 내부 주소·포트가 다를 수 있으므로 후자를 복사해 외부값으로 가정하지 마세요. 기대값을 확인할 수 없으면 CI 작업을 실행하지 않습니다.

사전 검증은 접속 URL·계정과 실제 연결의 `current_database()`(`dean_p_blog_ci`), `current_user`(`dean_p_blog_app_ci`), 서버 주소·포트, 계정 관리자 권한을 확인합니다. URL과 허용 대상을 동일한 잘못된 값으로 설정하면 두 입력의 일치만으로 안전을 보증하지 못합니다. 허용 대상과 서버 기대값은 서로 다른 근거로 확인하고, DB명·계정·권한 검사를 추가 방어선으로 유지합니다. 계정의 다른 DB 접속 권한 제한도 별도로 유지해야 합니다.

이 DB는 테스트가 스키마 전체를 지우므로 다른 작업과 공유하지 마세요. Jenkins는 CI DB 사용 작업을 직렬 실행하며 빌드가 성공해야 배포 단계로 넘어갑니다. CI 전용 DB 외에서는 이 작업을 실행하지 마세요. 운영 DB의 실제 별도 복원 시험과 장애 시 이미지 롤백은 아직 검증하지 않았습니다.

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

- [x] Java 25 런타임 확인
- [x] `prod` 프로필 확인
- [x] `DB_URL`, `DB_USER`, `DB_PASSWORD`를 안전하게 주입
- [x] 외부 PostgreSQL 연결 및 권한 확인
- [x] Flyway migration 이력 일치 및 Hibernate validate 시작 확인
- [x] 애플리케이션 기동 확인
- [ ] HTTPS 인증서와 리버스 프록시 설정
- [ ] 로그 위치와 보존 정책 확인
- [x] 백업 실행 확인
- [ ] 백업에서 실제 복구 테스트
- [ ] RSS, 사이트맵, robots.txt, 검색 메타데이터 확인
- [ ] 운영 장애 시 롤백 절차 확인

## Not Yet Verified

- 다른 LAN 장치에서의 접속 및 이후 변경의 자동 배포
- 운영 장애 시 이미지 롤백과 재부팅 후 동작
- HTTPS
- 백업의 실제 별도 DB 복원
- 모니터링과 알림
