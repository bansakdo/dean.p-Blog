// 목적: main 브랜치를 CI 전용 DB에서 검증(통합 테스트와 Playwright 브라우저 E2E)하고 Mac mini에 자동 배포합니다.
// 사용: Jenkins Pipeline을 Pipeline script from SCM으로 설정하고 이 파일을 지정합니다.
// 설계: CI 검증 후 제한된 SSH 키로 main 커밋만 전달합니다. 관련 스크립트: gradlew, Mac mini deploy.py.
pipeline {
  agent { label 'java25' }
  triggers { pollSCM('H/2 * * * *') }
  options {
    disableConcurrentBuilds()
    timeout(time: 30, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }
  stages {
    stage('Checkout main') {
      steps { git branch: 'main', url: 'https://github.com/bansakdo/dean.p-Blog.git' }
    }
    stage('Build and test') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dean-p-blog-ci-db', usernameVariable: 'CI_DB_USER', passwordVariable: 'CI_DB_PASSWORD')]) {
          // 에이전트 메모리(1.5GiB)를 Chromium E2E와 나눠 쓰므로 Gradle·테스트 JVM의 힙 밖 메모리(코드 캐시·GC·스레드 스택)도 줄인다.
          sh 'JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k" sh ./gradlew --no-daemon --max-workers=1 clean ciDbIntegrationTest ciDbE2eTest bootJar'
        }
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'build/test-results/*/*.xml'
          archiveArtifacts allowEmptyArchive: true, artifacts: 'build/reports/e2e/*.png'
        }
      }
    }
    stage('Archive') {
      steps { archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true }
    }
    stage('Deploy to Mac mini') {
      steps {
        withCredentials([sshUserPrivateKey(credentialsId: 'dean-p-blog-deploy-ssh', keyFileVariable: 'DEPLOY_KEY', usernameVariable: 'DEPLOY_USER')]) {
          sh '''
            set -eu
            DEPLOY_SHA="$(git rev-parse HEAD)"
            ssh -T -i "$DEPLOY_KEY" -o BatchMode=yes -o IdentitiesOnly=yes \
              -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$WORKSPACE/deploy/known_hosts" \
              "$DEPLOY_USER@host.docker.internal" "deploy $DEPLOY_SHA"
          '''
        }
      }
    }
  }
}
