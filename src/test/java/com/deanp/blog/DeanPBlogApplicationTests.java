package com.deanp.blog;

import com.deanp.blog.persistence.TestPostgresql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 외부 개발 DB를 변경하지 않고 전체 애플리케이션 구성을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
class DeanPBlogApplicationTests {

    private static final TestPostgresql DB = new TestPostgresql();

    /** @param registry 격리된 테스트 DB와 임의 포트를 등록할 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    /** 최신 마이그레이션과 실제 서비스 빈으로 컨텍스트가 시작되는지 검증한다. */
    @Test
    void contextLoads() {
    }
}
