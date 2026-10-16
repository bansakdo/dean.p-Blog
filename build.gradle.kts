plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.deanp"
version = "0.0.1-SNAPSHOT"
description = "dean.p personal blog"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	compileOnly("org.projectlombok:lombok")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
	implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
	implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.microsoft.playwright:playwright:1.63.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// 브라우저 E2E 테스트(@Tag("e2e"))는 느리므로 기본 test와 CI 통합 테스트에서 빼고 e2eTest·ciDbE2eTest로만 돌린다.
tasks.test {
	useJUnitPlatform { excludeTags("e2e") }
}

tasks.register<Test>("e2eTest") {
	group = "verification"
	description = "일회용 PostgreSQL 컨테이너와 Chromium으로 브라우저 E2E 테스트를 돌린다."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform { includeTags("e2e") }
}

val ciDbPreflight = tasks.register<JavaExec>("ciDbPreflight") {
	group = "verification"
	dependsOn(tasks.testClasses)
	classpath = sourceSets.test.get().runtimeClasspath
	mainClass = "com.deanp.blog.persistence.CiDatabaseGate"
	args("preflight")
}

val ciDbDriftTest = tasks.register<Test>("ciDbDriftTest") {
	group = "verification"
	dependsOn(ciDbPreflight)
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	systemProperty("blog.ci.db", "true")
	filter { includeTestsMatching("*BlogSchemaDriftMigrationTest") }
}

val ciDbReset = tasks.register<JavaExec>("ciDbReset") {
	group = "verification"
	dependsOn(ciDbDriftTest)
	classpath = sourceSets.test.get().runtimeClasspath
	mainClass = "com.deanp.blog.persistence.CiDatabaseGate"
	args("reset")
}

val ciDbSchemaTest = tasks.register<Test>("ciDbSchemaTest") {
	group = "verification"
	dependsOn(ciDbReset)
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	systemProperty("blog.ci.db", "true")
	filter { includeTestsMatching("*BlogSchemaVerificationTest") }
}

val ciDbIntegrationTest = tasks.register<Test>("ciDbIntegrationTest") {
	group = "verification"
	dependsOn(ciDbSchemaTest)
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	systemProperty("blog.ci.db", "true")
	useJUnitPlatform { excludeTags("e2e") }
	filter {
		excludeTestsMatching("*BlogSchemaDriftMigrationTest")
		excludeTestsMatching("*BlogSchemaVerificationTest")
	}
}

// CI 전용 DB에서 브라우저 E2E 테스트를 돌린다. 데이터를 커밋하므로 통합 테스트가 끝난 뒤에 실행한다.
tasks.register<Test>("ciDbE2eTest") {
	group = "verification"
	dependsOn(ciDbSchemaTest)
	mustRunAfter(ciDbIntegrationTest)
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	systemProperty("blog.ci.db", "true")
	useJUnitPlatform { includeTags("e2e") }
}
