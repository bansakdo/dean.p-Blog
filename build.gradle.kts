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
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
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

tasks.register<Test>("ciDbIntegrationTest") {
	group = "verification"
	dependsOn(ciDbSchemaTest)
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	systemProperty("blog.ci.db", "true")
	filter {
		excludeTestsMatching("*BlogSchemaDriftMigrationTest")
		excludeTestsMatching("*BlogSchemaVerificationTest")
	}
}
