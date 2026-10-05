# 개발 서버

로컬 `dev` 앱은 `8081`을 사용합니다. 별도 Docker 개발 서버는 Mac mini LAN 주소 `http://192.168.1.55:63051/`에서 `dev` 브랜치 커밋을 제공합니다. 운영 Docker(`63050`) 및 운영 DB는 사용하지 않습니다.

- 컨테이너: `dean-p-blog-dev`, 내부 `8080`, `SPRING_PROFILES_ACTIVE=dev`
- 외부 설정: `/Users/macmini/services/dean-p-blog-dev/compose.yaml`, `secrets/.env.dev`(0600), `media/`, `deploy.py`, `current-release`
- 이미지 경로: 호스트 `media/posts/{글 ID}/{파일명}`을 컨테이너 `/app/media/posts/...`로 마운트하며 Spring이 `/media/posts/**`만 제공합니다. 기존 `media/media/posts/` 파일은 새 위치에 복사·검증 후 보관 중이므로 검증 전에는 삭제하지 마세요. 로컬 `8081`의 기본 저장소는 `../dean-p-blog-media`입니다.
- DB: 외부 `dean_p_blog_dev`; 이미지에는 DB 컨테이너나 비밀 파일이 없습니다.
- Jenkins 전용 작업: Pipeline script from SCM, Git 저장소 `https://github.com/bansakdo/dean.p-Blog.git`, 브랜치 `*/dev`, 스크립트 `Jenkinsfile.dev`. `java25` 에이전트의 단일 executor가 `blog-main`과 CI 전용 DB 테스트를 직렬화합니다. CI 통합 테스트에서 Gradle과 테스트 JVM이 동시에 실행되므로, Java 에이전트 컨테이너는 메모리 한도 1536 MiB로 운영하고 개발 파이프라인 JVM 힙을 제한합니다. 다른 executor를 추가할 때는 DB 공유 잠금 또는 전용 CI DB가 필요합니다.
- 인증: CI DB에는 기존 Jenkins Credential `dean-p-blog-ci-db`를 사용합니다. 개발 배포 SSH 개인키는 Jenkins Java 에이전트의 `/run/secrets/dean-p-blog-dev-deploy`에 호스트 파일을 읽기 전용으로 마운트합니다(호스트 파일 0600, 이미지·저장소에 미포함). 공개키는 Mac mini `authorized_keys`에서 개발용 `deploy.py` 강제 명령으로 제한합니다. 운영 배포 키는 사용하지 않습니다. 이전에 UI에 등록한 `dean-p-blog-dev-deploy-ssh` Credential은 이 구성에서 사용하지 않으므로 관리자 확인 후 삭제할 수 있습니다.
- SCM 폴링: 약 2분 간격으로 `dev` 변경 감지 → CI DB 사전검사·전체 테스트·JAR 빌드 → 검증된 커밋 SHA만 SSH로 전달 → 개발 이미지만 빌드·교체 → HTTP 기동 검사. 감지 이후 빌드·배포 시간이 추가됩니다. 실패 시 이전 개발 이미지로 돌아갑니다. Flyway가 적용한 DB 변경은 이미지 롤백으로 되돌릴 수 없습니다.
- 직접 확인: `docker ps --filter name=dean-p-blog-dev`, `curl -fsS http://192.168.1.55:63051/`, `git -C /Users/macmini/services/dean-p-blog-dev/source rev-parse HEAD`, `cat /Users/macmini/services/dean-p-blog-dev/current-release` (비밀 파일 내용은 출력하지 마세요).

호스트 IP가 바뀌면 개발 Compose의 바인딩과 `deploy.py` 기동 검사 주소를 함께 변경해야 합니다. `dev` 커밋으로 새 migration을 배포하기 전에 개발 DB를 백업하고 호환성을 검토하세요.
