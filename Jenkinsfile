// 목적: main 브랜치를 빌드·테스트하고 운영 배포는 안전 조건 충족 전까지 차단합니다.
// 사용: Jenkins Pipeline을 Pipeline script from SCM으로 설정하고 이 파일을 지정합니다.
// 설계: 빌드·테스트 후 명시적 안전 게이트에서 중단합니다. 관련 스크립트: gradlew.
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
      steps { sh 'sh ./gradlew --no-daemon --max-workers=1 clean test bootJar' }
      post { always { junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml' } }
    }
    stage('Archive') {
      steps { archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true }
    }
    stage('Production safety gate') {
      steps {
        error('Deployment blocked: remote main uses Hibernate ddl-auto=update and baseline-on-migrate=true. Production DB backup and migration validation must be established before enabling automatic deployment.')
      }
    }
  }
}
