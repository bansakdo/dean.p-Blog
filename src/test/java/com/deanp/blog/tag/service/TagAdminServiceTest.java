package com.deanp.blog.tag.service;

import com.deanp.blog.persistence.TestPostgresql;
import com.deanp.blog.tag.service.TagAdminService.TagRow;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 격리된 PostgreSQL에서 태그 관리 규칙을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TagAdminServiceTest {

    private static final TestPostgresql DB = new TestPostgresql();

    @Autowired
    private TagAdminService service;
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

    /** 글 세 편과 태그 세 개를 준비한다. 글 20은 spring·Spring Boot, 글 21은 spring, 글 22는 Spring Boot·java. */
    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, 'tag-user', 'hash', '작성자')", id(1));
        for (int number = 20; number <= 22; number++) {
            jdbc.update("INSERT INTO blog.post_detail (id, author_id, slug, title, content, status) VALUES (?, ?, ?, ?, '본문', 'DRAFT')",
                    id(number), id(1), "post-" + number, "글 " + number);
        }
        tag(10, "spring");
        tag(11, "Spring Boot");
        tag(12, "java");
        link(20, 10);
        link(20, 11);
        link(21, 10);
        link(22, 11);
        link(22, 12);
    }

    /** 목록은 이름순이고 각 태그가 붙은 글 수를 센다. */
    @Test
    void listsTagsWithCounts() {
        assertThat(service.findAll()).extracting(TagRow::name).containsExactly("java", "spring", "Spring Boot");
        assertThat(service.findAll()).extracting(TagRow::postCount).containsExactly(1L, 2L, 2L);
    }

    /** 이름을 바꾸면 주소를 비웠을 때 새 이름으로 만들고, 다른 태그와 이름이 겹치면 거부한다. */
    @Test
    void renamesTag() {
        service.rename(id(10), "Spring Framework", "");
        entityManager.flush();
        assertThat(service.find(id(10)).orElseThrow().slug()).isEqualTo("spring-framework");

        assertThatThrownBy(() -> service.rename(id(12), "spring boot", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rename(id(12), "자바", "spring-boot")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rename(id(12), "Java, Kotlin", "")).isInstanceOf(IllegalArgumentException.class);
        service.rename(id(12), "Java", "java");
    }

    /** 병합하면 원래 태그의 글에 대상 태그가 붙고 원래 태그는 사라지며, 이미 둘 다 붙은 글은 하나만 남는다. */
    @Test
    void mergesTags() {
        service.merge(id(10), id(11));
        entityManager.flush();

        assertThat(tagNames(20)).containsExactly("Spring Boot");
        assertThat(tagNames(21)).containsExactly("Spring Boot");
        assertThat(service.find(id(10))).isEmpty();
        assertThat(service.find(id(11)).orElseThrow().postCount()).isEqualTo(3);
        assertThatThrownBy(() -> service.merge(id(11), id(11))).isInstanceOf(IllegalArgumentException.class);
    }

    /** 삭제하면 태그와 연결만 사라지고 글은 남는다. */
    @Test
    void deletesTag() {
        service.delete(id(11));
        entityManager.flush();

        assertThat(tagNames(20)).containsExactly("spring");
        assertThat(tagNames(22)).containsExactly("java");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog.post_detail", Integer.class)).isEqualTo(3);
    }

    /** @param number 식별자 @param name 이름 */
    private void tag(int number, String name) {
        jdbc.update("INSERT INTO blog.tag (id, name, slug) VALUES (?, ?, ?)", id(number), name, name.toLowerCase().replace(' ', '-'));
    }

    /** @param post 글 식별자 숫자 @param tag 태그 식별자 숫자 */
    private void link(int post, int tag) {
        jdbc.update("INSERT INTO blog.post_tag (post_detail_id, tag_id) VALUES (?, ?)", id(post), id(tag));
    }

    /** @param post 글 식별자 숫자 @return 글에 붙은 태그 이름, 이름순 */
    private List<String> tagNames(int post) {
        return jdbc.queryForList("SELECT t.name FROM blog.post_tag pt JOIN blog.tag t ON t.id = pt.tag_id WHERE pt.post_detail_id = ? ORDER BY t.name",
                String.class, id(post));
    }

    /** @param number 식별자 숫자 @return 고정 테스트 UUID */
    private static UUID id(int number) {
        return new UUID(0, number);
    }
}
