package com.deanp.blog.post.service;

import com.deanp.blog.persistence.TestPostgresql;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.service.CategoryAdminService.CategoryInput;
import com.deanp.blog.post.service.CategoryAdminService.CategoryRow;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 격리된 PostgreSQL에서 카테고리 관리 규칙을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CategoryAdminServiceTest {
    /** V12가 만든 개발·노트 게시판 식별자 */
    private static final UUID DEV_BOARD = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID NOTES_BOARD = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private static final TestPostgresql DB = new TestPostgresql();

    @Autowired
    private CategoryAdminService service;
    @Autowired
    private PostDetailRepository posts;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    /** @param registry 테스트 전용 데이터베이스 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    /** 작성자를 준비한다. */
    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, 'category-user', 'hash', '작성자')", id(1));
    }

    /** 카테고리를 다른 게시판으로 옮기면 그 카테고리의 글도 함께 옮겨진다. 게시판을 고르지 않은 수정은 게시판을 유지한다. */
    @Test
    void movesPostsWithCategoryBoard() {
        UUID category = service.create(new CategoryInput("옮길 분류", "move-me", 0));
        entityManager.flush();
        post(40, category, "PUBLISHED");
        service.update(category, new CategoryInput("옮길 분류", "move-me", 1));
        entityManager.flush();
        assertThat(boardOf("post_category", category)).isEqualTo(DEV_BOARD);

        service.update(category, new CategoryInput("옮길 분류", "move-me", 1, NOTES_BOARD));
        entityManager.flush();

        assertThat(boardOf("post_category", category)).isEqualTo(NOTES_BOARD);
        assertThat(boardOf("post_detail", id(40))).isEqualTo(NOTES_BOARD);
    }

    /** @param table 테이블 이름 @param rowId 행 식별자 @return 게시판 식별자 */
    private UUID boardOf(String table, UUID rowId) {
        return jdbc.queryForObject("SELECT board_id FROM blog." + table + " WHERE id = ?", UUID.class, rowId);
    }

    /** 주소를 비우면 이름으로 만들고, 목록은 정렬 순서와 이름순이며 공개 카테고리 목록도 같은 순서를 따른다. */
    @Test
    void createsAndOrdersCategories() {
        UUID life = service.create(new CategoryInput("일상 기록", "", 2));
        UUID dev = service.create(new CategoryInput("Dev Notes", "", 1));
        entityManager.flush();
        post(20, dev, "PUBLISHED");
        post(21, life, "PUBLISHED");
        post(22, life, "DELETED");
        entityManager.flush();

        assertThat(service.findAll()).extracting(CategoryRow::slug).containsExactly("dev-notes", "일상-기록");
        assertThat(service.findAll()).extracting(CategoryRow::postCount).containsExactly(1L, 1L);
        assertThat(posts.findPublishedCategories(null)).extracting(option -> option.slug()).containsExactly("dev-notes", "일상-기록");

        service.update(life, new CategoryInput("일상 기록", "life", 0));
        entityManager.flush();
        assertThat(posts.findPublishedCategories(null)).extracting(option -> option.slug()).containsExactly("life", "dev-notes");
    }

    /** 이름은 대소문자와 관계없이, 주소는 그대로 겹치면 거부하고 금지 문자가 든 주소도 거부한다. */
    @Test
    void rejectsDuplicatesAndInvalidSlug() {
        UUID dev = service.create(new CategoryInput("Dev", "dev", 0));
        UUID life = service.create(new CategoryInput("Life", "life", 0));

        assertThatThrownBy(() -> service.create(new CategoryInput("dev", "other", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(life, new CategoryInput("Life", "dev", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new CategoryInput("새것", "a/b", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new CategoryInput(" ", "", 0))).isInstanceOf(IllegalArgumentException.class);
        service.update(dev, new CategoryInput("DEV", "dev", 5));
    }

    /** 삭제하지 않은 글이나 시리즈가 쓰면 지우지 않고, 삭제한 글만 쓰면 그 글의 카테고리를 비우고 지운다. */
    @Test
    void deletesOnlyUnusedCategory() {
        UUID dev = service.create(new CategoryInput("Dev", "dev", 0));
        entityManager.flush();
        post(20, dev, "DRAFT");
        entityManager.flush();
        assertThatThrownBy(() -> service.delete(dev)).isInstanceOf(IllegalArgumentException.class);

        jdbc.update("UPDATE blog.post_detail SET status = 'DELETED' WHERE id = ?", id(20));
        jdbc.update("INSERT INTO blog.post_series (id, category_id, slug, name) VALUES (?, ?, 'series', '시리즈')", id(30), dev);
        entityManager.clear();
        assertThatThrownBy(() -> service.delete(dev)).isInstanceOf(IllegalArgumentException.class);

        jdbc.update("DELETE FROM blog.post_series WHERE id = ?", id(30));
        entityManager.clear();
        service.delete(dev);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT category_id FROM blog.post_detail WHERE id = ?", UUID.class, id(20))).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog.post_category", Integer.class)).isZero();
    }

    /** @param number 식별자 @param categoryId 카테고리 식별자 @param status 글 상태 */
    private void post(int number, UUID categoryId, String status) {
        jdbc.update("INSERT INTO blog.post_detail (id, category_id, author_id, slug, title, content, status, published_at) VALUES (?, ?, ?, ?, ?, '본문', ?, now() - interval '1 minute')",
                id(number), categoryId, id(1), "post-" + number, "글 " + number, status);
    }

    /** @param number 식별자 숫자 @return 고정 테스트 UUID */
    private static UUID id(int number) {
        return new UUID(0, number);
    }
}
