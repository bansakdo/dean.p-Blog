package com.deanp.blog.post.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 격리된 PostgreSQL에서 시리즈 서비스의 일관성 규칙을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Testcontainers
class PostSeriesServiceTest {

    @Container
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private PostSeriesService service;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    /** @param registry 테스트 전용 데이터베이스 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", DB::getJdbcUrl);
        registry.add("DB_USER", DB::getUsername);
        registry.add("DB_PASSWORD", DB::getPassword);
        registry.add("WAS_PORT", () -> "0");
    }

    /** 서비스 검증에 필요한 카테고리와 글을 준비한다. */
    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, 'series-user', 'hash', '작성자')", id(1));
        category(10, "dev");
        category(11, "life");
        category(12, "other");
        post(20, 10, "first");
        post(21, 11, "second");
        post(22, 12, "other");
    }

    /** 배치 시 게시글 카테고리는 시리즈 대표 카테고리로 자동 동기화된다. */
    @Test
    void assignsPostToSeriesAndSynchronizesCategory() {
        UUID seriesId = service.createSeries(command(10, "boot", "부트", null));

        service.assignPost(seriesId, id(21), 1);
        entityManager.flush();

        assertThat(column("category_id", 21)).isEqualTo(id(10));
        assertThat(column("series_id", 21)).isEqualTo(seriesId);
        assertThat(numberColumn("series_order", 21)).isEqualTo(1);
    }

    /** 대표 카테고리 변경은 연결된 게시글 카테고리를 함께 변경한다. */
    @Test
    void updatesSeriesCategoryAndLinkedPosts() {
        UUID seriesId = service.createSeries(command(10, "boot", "부트", null));
        service.assignPost(seriesId, id(20), 1);

        service.updateSeries(seriesId, command(11, "life-series", "일상", "소개"));
        entityManager.flush();

        assertThat(column("category_id", 20)).isEqualTo(id(11));
    }

    /** 없는 카테고리, 중복 slug, 중복 순서는 명시적으로 거부한다. */
    @Test
    void rejectsUnknownCategoryDuplicateSlugAndDuplicateOrder() {
        UUID seriesId = service.createSeries(command(10, "boot", "부트", null));
        service.assignPost(seriesId, id(20), 1);

        assertThatThrownBy(() -> service.createSeries(command(99, "bad", "오류", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSeries(command(12, "boot", "중복", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignPost(seriesId, id(21), 1))
                .isInstanceOf(IllegalArgumentException.class);
        service.assignPost(seriesId, id(22), 2);
        entityManager.flush();
        assertThat(column("category_id", 22)).isEqualTo(id(10));
    }

    /** 게시글 시리즈 연결만 제거하고 카테고리는 현재 값을 유지한다. */
    @Test
    void removesPostFromSeries() {
        UUID seriesId = service.createSeries(command(10, "boot", "부트", null));
        service.assignPost(seriesId, id(20), 1);

        entityManager.flush();
        entityManager.clear();
        service.removePost(id(20));
        entityManager.flush();

        assertThat(column("series_id", 20)).isNull();
        assertThat(numberColumn("series_order", 20)).isNull();
        assertThat(column("category_id", 20)).isEqualTo(id(10));
    }

    /** @param category 카테고리 식별자 @param slug URL 값 @param name 표시명 @param description 소개 @return 명령 */
    private PostSeriesCommand command(int category, String slug, String name, String description) {
        return new PostSeriesCommand(id(category), slug, name, description, 0);
    }

    /** @param number 식별자 숫자 @return 고정 테스트 UUID */
    private UUID id(int number) {
        return new UUID(0, number);
    }

    /** @param number 식별자 @param slug URL 값 */
    private void category(int number, String slug) {
        jdbc.update("INSERT INTO blog.post_category (id, slug, name) VALUES (?, ?, ?)",
                id(number), slug, slug);
    }

    /** @param number 식별자 @param category 카테고리 @param slug URL 값 */
    private void post(int number, int category, String slug) {
        jdbc.update("INSERT INTO blog.post_detail (id, category_id, author_id, slug, title, content, status, published_at) VALUES (?, ?, ?, ?, ?, '본문', 'PUBLISHED', now())",
                id(number), id(category), id(1), slug, slug);
    }

    /** @param column 컬럼명 @param post 게시글 숫자 식별자 @return UUID 컬럼 값 */
    private UUID column(String column, int post) {
        return jdbc.queryForObject("SELECT " + column + " FROM blog.post_detail WHERE id = ?", UUID.class, id(post));
    }

    /** @param column 컬럼명 @param post 게시글 숫자 식별자 @return 정수 컬럼 값 */
    private Integer numberColumn(String column, int post) {
        return jdbc.queryForObject("SELECT " + column + " FROM blog.post_detail WHERE id = ?", Integer.class, id(post));
    }
}
