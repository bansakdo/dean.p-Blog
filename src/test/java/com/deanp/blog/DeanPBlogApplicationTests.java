package com.deanp.blog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 외부 개발 DB를 변경하지 않고 전체 애플리케이션 구성을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class DeanPBlogApplicationTests {

    @Container
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    /** @param registry 격리된 테스트 DB와 임의 포트를 등록할 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", DB::getJdbcUrl);
        registry.add("DB_USER", DB::getUsername);
        registry.add("DB_PASSWORD", DB::getPassword);
        registry.add("WAS_PORT", () -> "0");
    }

    /** 최신 마이그레이션과 실제 서비스 빈으로 컨텍스트가 시작되는지 검증한다. */
    @Test
    void contextLoads() {
    }
}
