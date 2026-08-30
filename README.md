# dean.p

Spring Boot 서버 렌더링 방식으로 만드는 개인 블로그입니다.

## 현재 범위

- Java 25, Spring Boot 4.1.1, Gradle
- Thymeleaf 기반 홈·글 목록·글 본문·소개 화면
- 반응형 라이트/다크 테마
- 화면 검증용 메모리 샘플 글
- `dev`/`prod` 프로필별 외부 PostgreSQL 연결

현재 범위는 화면 템플릿입니다. 샘플 글은 실제 DB 데이터가 아니며, 외부 PostgreSQL 연결과 Obsidian/Git 자동 발행은 후속 단계입니다.

## 실행

```bash
./gradlew bootRun
```

Gradle toolchain이 Java 25를 사용하므로, 시스템 기본 Java가 Java 25이거나 Gradle이 Java 25 toolchain을 찾을 수 있어야 합니다. 확인하려면 `java -version`을 실행합니다.

브라우저에서 `http://localhost:8080`을 엽니다.

## 테스트

```bash
./gradlew test
```

## DB 프로필 실행

DB 환경변수는 루트의 `.env.dev` 또는 `.env.prod`에서 관리합니다. 두 파일은 Git에서 제외되어 있으므로 운영 비밀번호를 저장소에 커밋하지 않습니다.

개발 환경:

```bash
SPRING_PROFILES_ACTIVE=dev \
./gradlew bootRun
```

운영 환경:

```bash
SPRING_PROFILES_ACTIVE=prod \
./gradlew bootRun
```

프로필 설정이 대응하는 `.env.*` 파일을 자동으로 읽습니다. `.env.dev`와 `.env.prod`의 DB 주소·사용자·비밀번호를 실제 값으로 바꿔야 합니다. 프로필을 지정하지 않으면 기본 프로필로 화면 템플릿만 실행합니다.

## 주요 경로

- `/` 홈
- `/posts` 글 목록
- `/posts/{slug}` 글 본문
- `/about` 소개

## 디자인 원칙

- 글 본문 폭을 약 740px로 제한해 읽기에 집중합니다.
- 넓은 여백, 부드러운 강조색, 정돈된 카드 리듬을 사용합니다.
- 다크 모드는 순수 검정 대신 따뜻한 차콜 배경을 사용합니다.
- 별도 프론트엔드 서버 없이 Spring Boot가 완성된 HTML과 정적 자산을 제공합니다.
