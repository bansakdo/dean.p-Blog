# Production

## Status

- 문서 상태: 초안
- 마지막 갱신: 2026-08-30
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

현재 `dev`와 `prod`의 `ddl-auto`는 명시적 임시 결정에 따라 `update`입니다. 운영 배포 전에는 자동 스키마 변경 위험을 검토하고 `validate` 또는 확정된 migration 방식으로 전환해야 합니다.

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

게시글 테이블과 마이그레이션 전략은 아직 확정되지 않았습니다.

## Deployment Checklist

- [ ] Java 25 런타임 확인
- [ ] `prod` 프로필 확인
- [ ] `DB_URL`, `DB_USER`, `DB_PASSWORD`를 안전하게 주입
- [ ] 외부 PostgreSQL 연결 및 권한 확인
- [ ] 애플리케이션 기동 확인
- [ ] HTTPS 인증서와 리버스 프록시 설정
- [ ] 로그 위치와 보존 정책 확인
- [ ] 백업 실행 확인
- [ ] 백업에서 실제 복구 테스트
- [ ] RSS, 사이트맵, robots.txt, 검색 메타데이터 확인
- [ ] 운영 장애 시 롤백 절차 확인

## Not Yet Verified

- 실제 운영 서버 배포
- 외부 PostgreSQL 접속
- 스키마 및 마이그레이션 적용
- HTTPS
- 백업·복구
- 모니터링과 알림
