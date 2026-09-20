package com.deanp.blog.post.persistence.query;

import com.deanp.blog.post.PostFilterOption;
import com.deanp.blog.post.PostSeriesOption;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
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

/** 격리된 PostgreSQL에서 공개 글 필터와 선택지 조회를 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Testcontainers
class PostFilterQueryTest {
    @Container
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private PostDetailRepository posts;
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

    /** 공개 글, 태그 없는 글, 초안과 미발행 글을 테스트 트랜잭션에 준비한다. */
    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, 'filter-user', 'hash', '작성자')", id(1));
        jdbc.update("INSERT INTO blog.post (id, post_code, name) VALUES (?, 'filter-blog', 'Blog')", id(2));
        category(3, "dev", "개발");
        category(4, "life", "일상");
        category(5, "private", "비공개분류");
        tag(6, "java", "Java");
        tag(7, "spring", "Spring");
        tag(8, "private-tag", "비공개태그");
        post(10, 3, "dev-post", "PUBLISHED", true);
        post(11, 4, "life-post", "PUBLISHED", true);
        post(12, 3, "untagged-post", "PUBLISHED", true);
        post(13, 5, "draft-post", "DRAFT", true);
        post(14, 5, "unpublished-post", "PUBLISHED", false);
        series(20, 3, "boot-camp", "부트 캠프", "기초부터 순서대로");
        series(21, 4, "life-log", "일상 기록", "느린 기록");
        series(22, 5, "draft-only", "초안 전용", "공개 전");
        assignSeries(10, 20, 2);
        assignSeries(12, 20, 1);
        assignSeries(11, 21, 1);
        assignSeries(13, 22, 1);
        link(10, 6);
        link(10, 7);
        link(11, 6);
        link(13, 8);
        link(14, 8);
    }

    /** 태그를 선택해도 일치한 글의 다른 태그가 사라지지 않는다. */
    @Test
    void combinesFiltersAndPreservesAllTags() {
        assertThat(posts.findPublishedRowsNewestFirst("dev", "java"))
                .extracting(PublicPostRow::slug).containsOnly("dev-post");
        assertThat(posts.findPublishedRowsNewestFirst("dev", "java"))
                .extracting(PublicPostRow::tagName).containsExactly("Java", "Spring");
        assertThat(posts.findPublishedRowsNewestFirst(null, "java"))
                .extracting(PublicPostRow::slug).contains("dev-post", "life-post");
        assertThat(posts.findPublishedRowsNewestFirst("life", "spring")).isEmpty();
    }

    /** 공개 글에 사용된 분류만 중복 없이 선택지로 노출한다. */
    @Test
    void optionsExcludeDraftAndUnpublishedMetadata() {
        assertThat(posts.findPublishedCategories()).extracting(PostFilterOption::slug)
                .containsExactly("dev", "life");
        assertThat(posts.findPublishedTags()).extracting(PostFilterOption::slug)
                .containsExactly("java", "spring");
    }

    /** 공백 조건은 전체 조회이며 존재하지 않는 조건은 빈 결과다. */
    @Test
    void handlesBlankAndUnknownFilters() {
        assertThat(posts.findPublishedRowsNewestFirst(" ", " "))
                .extracting(PublicPostRow::slug).containsOnly("dev-post", "life-post", "untagged-post");
        assertThat(posts.findPublishedRowsNewestFirst("missing", null)).isEmpty();
        assertThat(posts.findPublishedRowsNewestFirst(null, "missing")).isEmpty();
        assertThat(posts.findPublishedRowsNewestFirst("dev"))
                .extracting(PublicPostRow::slug).contains("untagged-post");
    }

    /** 시리즈 선택 시 카테고리 불일치는 빈 결과이며 시리즈 순서대로 정렬한다. */
    @Test
    void filtersBySeriesWithoutBroadeningMismatchedCategory() {
        assertThat(posts.findPublishedRows("dev", "boot-camp", null, null))
                .extracting(PublicPostRow::slug)
                .containsExactly("untagged-post", "dev-post", "dev-post");
        assertThat(posts.findPublishedRows("life", "boot-camp", null, null)).isEmpty();
        assertThat(posts.findPublishedRows("dev", "missing", null, null)).isEmpty();
    }

    /** 검색어는 카테고리, 시리즈, 태그 조건과 함께 적용하고 표시 태그는 보존한다. */
    @Test
    void combinesSearchWithOtherFiltersAndPreservesTags() {
        assertThat(posts.findPublishedRows("dev", "boot-camp", "spring", "dev-post"))
                .extracting(PublicPostRow::tagName)
                .containsExactly("Java", "Spring");
        assertThat(posts.findPublishedRows("dev", "boot-camp", null, "없는검색어")).isEmpty();
    }

    /** 공개 글이 있는 시리즈만 선택지와 소개로 노출한다. */
    @Test
    void seriesOptionsExcludeDraftOnlySeries() {
        assertThat(posts.findPublishedSeries("dev")).extracting(PostSeriesOption::slug)
                .containsExactly("boot-camp");
        assertThat(posts.findPublishedSeries(null)).extracting(PostSeriesOption::slug)
                .containsExactly("boot-camp", "life-log");
        assertThat(posts.findPublishedSeries("dev", "boot-camp"))
                .hasValueSatisfying(option -> assertThat(option.description()).isEqualTo("기초부터 순서대로"));
        assertThat(posts.findPublishedSeries("life", "boot-camp")).isEmpty();
    }

    /** 편수는 태그 중복·초안·예약 글을 제외하고, 완결 상태와 원래 연재 번호를 보존한다. */
    @Test
    void countsPublishedPostsAndPreservesSeriesMetadata() {
        post(15, 5, "future-post", "PUBLISHED", true);
        jdbc.update("UPDATE blog.post_detail SET published_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().plus(10, java.time.temporal.ChronoUnit.DAYS)), id(15));
        assignSeries(15, 22, 2);
        jdbc.update("UPDATE blog.post_series SET status = 'COMPLETED' WHERE id = ?", id(20));
        assertThat(posts.countPublishedPosts()).isEqualTo(3);
        assertThat(posts.findPublishedCategories()).extracting(PostFilterOption::postCount).containsExactly(2L, 1L);
        assertThat(posts.findPublishedSeries("dev", "boot-camp")).hasValueSatisfying(option -> {
            assertThat(option.postCount()).isEqualTo(2);
            assertThat(option.statusLabel()).isEqualTo("완결");
        });
        assertThat(posts.findPublishedSeries(null)).extracting(PostSeriesOption::slug).doesNotContain("draft-only");
        assertThat(posts.findPublishedRows("dev", "boot-camp", "java", null)).allSatisfy(row -> {
            assertThat(row.seriesOrder()).isEqualTo(2);
            assertThat(row.seriesName()).isEqualTo("부트 캠프");
            assertThat(row.seriesSlug()).isEqualTo("boot-camp");
            assertThat(row.categoryName()).isEqualTo("개발");
        });
        assertThat(posts.findPublishedRowsBySlug("future-post")).isEmpty();
        assertThat(posts.findPublishedTags()).extracting(PostFilterOption::slug).doesNotContain("private-tag");
    }

    /** 비공개 시리즈의 제목·링크·선택지는 공개 글에서도 노출하지 않는다. */
    @Test
    void doesNotExposeInactiveSeriesMetadata() {
        jdbc.update("UPDATE blog.post_series SET status = 'INACTIVE' WHERE id = ?", id(20));
        assertThat(posts.findPublishedSeries("dev")).isEmpty();
        assertThat(posts.findPublishedRows("dev", "boot-camp", null, null)).isEmpty();
        assertThat(posts.findPublishedRows("dev", null, null, null)).isNotEmpty().allSatisfy(row -> {
            assertThat(row.seriesSlug()).isNull();
            assertThat(row.seriesName()).isNull();
        });
    }

    /** @param number 식별자 숫자 @return 고정 테스트 UUID */
    private UUID id(int number) { return new UUID(0, number); }

    /** @param number 식별자 @param slug URL 값 @param name 표시명 */
    private void category(int number, String slug, String name) {
        jdbc.update("INSERT INTO blog.post_category (id, post_id, slug, name) VALUES (?, ?, ?, ?)", id(number), id(2), slug, name);
    }

    /** @param number 식별자 @param slug URL 값 @param name 표시명 */
    private void tag(int number, String slug, String name) {
        jdbc.update("INSERT INTO blog.tag (id, slug, name) VALUES (?, ?, ?)", id(number), slug, name);
    }

    /** @param number 식별자 @param category 분류 식별자 @param slug URL 값 @param status 공개 상태 @param dated 발행일 존재 여부 */
    private void post(int number, int category, String slug, String status, boolean dated) {
        jdbc.update("INSERT INTO blog.post_detail (id, post_id, category_id, author_id, slug, title, content, status, published_at) VALUES (?, ?, ?, ?, ?, ?, '본문', ?, ?)",
                id(number), id(2), id(category), id(1), slug, slug, status,
                dated ? java.sql.Timestamp.from(java.time.Instant.parse("2026-09-01T00:00:00Z")) : null);
    }

    /** @param number 식별자 @param category 대표 카테고리 @param slug URL 값 @param name 표시명 @param description 소개 */
    private void series(int number, int category, String slug, String name, String description) {
        jdbc.update("INSERT INTO blog.post_series (id, post_id, category_id, slug, name, description) VALUES (?, ?, ?, ?, ?, ?)",
                id(number), id(2), id(category), slug, name, description);
    }

    /** @param post 글 식별자 @param series 시리즈 식별자 @param order 시리즈 순서 */
    private void assignSeries(int post, int series, int order) {
        jdbc.update("UPDATE blog.post_detail SET series_id = ?, series_order = ? WHERE id = ?",
                id(series), order, id(post));
    }

    /** @param post 글 식별자 @param tag 태그 식별자 */
    private void link(int post, int tag) {
        jdbc.update("INSERT INTO blog.post_tag (post_detail_id, tag_id) VALUES (?, ?)", id(post), id(tag));
    }
}
