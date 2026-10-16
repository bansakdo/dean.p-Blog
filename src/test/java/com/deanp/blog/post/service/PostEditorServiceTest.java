package com.deanp.blog.post.service;

import com.deanp.blog.persistence.TestPostgresql;
import com.deanp.blog.post.service.PostEditCommand.PublishAction;
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

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 격리된 PostgreSQL에서 글 작성·수정 서비스의 저장 규칙을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PostEditorServiceTest {

    private static final TestPostgresql DB = new TestPostgresql();
    private static final String AUTHOR = "editor-user";

    @Autowired
    private PostEditorService service;
    @Autowired
    private PostSeriesService seriesService;
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

    /** 작성자와 카테고리 두 개를 준비한다. */
    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, ?, 'hash', '작성자')", id(1), AUTHOR);
        jdbc.update("INSERT INTO blog.post_category (id, slug, name) VALUES (?, 'editor-dev', '개발')", id(10));
        jdbc.update("INSERT INTO blog.post_category (id, slug, name) VALUES (?, 'editor-life', '일상')", id(11));
    }

    /** 새 글은 초안으로 저장되고 같은 이름의 태그는 대소문자와 관계없이 한 번만 연결된다. */
    @Test
    void createsDraftWithDeduplicatedTags() {
        UUID postId = service.create(command("첫 글", "", List.of("Spring", "spring", " 자바 "), PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        assertThat(text("status", postId)).isEqualTo("DRAFT");
        assertThat(text("slug", postId)).isEqualTo("첫-글");
        assertThat(jdbc.queryForObject("SELECT published_at FROM blog.post_detail WHERE id = ?", Timestamp.class, postId)).isNull();
        assertThat(tagNames(postId)).containsExactlyInAnyOrder("Spring", "자바");
    }

    /** 주소가 겹치는 다른 이름의 태그는 숫자를 붙인 주소로 만들어 글 저장을 막지 않는다. */
    @Test
    void suffixesCollidingTagSlug() {
        jdbc.update("INSERT INTO blog.tag (id, name, slug) VALUES (?, 'C', 'c')", id(30));

        UUID postId = service.create(command("태그 충돌", "tag-collision", List.of("C#", "C"), PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT slug FROM blog.tag WHERE name = 'C#'", String.class)).isEqualTo("c-2");
        assertThat(tagNames(postId)).containsExactlyInAnyOrder("C", "C#");
    }

    /** 태그 주소에는 글 주소와 같은 금지 문자를 남기지 않고, 쓸 글자가 없으면 거부한다. */
    @Test
    void stripsForbiddenCharactersFromTagSlug() {
        service.create(command("특수 문자", "special-tag", List.of("A<b>/C D"), PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT slug FROM blog.tag WHERE name = 'A<b>/C D'", String.class)).isEqualTo("abc-d");
        assertThatThrownBy(() -> service.create(command("빈 태그", "empty-tag", List.of("?#"), PublishAction.KEEP), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 다른 글이 쓰는 주소로는 만들거나 고칠 수 없다. */
    @Test
    void rejectsDuplicateSlug() {
        UUID first = service.create(command("하나", "same-slug", List.of(), PublishAction.KEEP), AUTHOR);
        UUID second = service.create(command("둘", "other-slug", List.of(), PublishAction.KEEP), AUTHOR);

        assertThatThrownBy(() -> service.create(command("셋", "same-slug", List.of(), PublishAction.KEEP), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(second, command("둘", "same-slug", List.of(), PublishAction.KEEP)))
                .isInstanceOf(IllegalArgumentException.class);

        // 자기 주소를 그대로 저장하는 것은 허용한다.
        service.update(first, command("하나 수정", "same-slug", List.of(), PublishAction.KEEP));
        entityManager.flush();
        assertThat(text("title", first)).isEqualTo("하나 수정");
    }

    /** 태그 수정은 빠진 연결을 지우고 새 연결만 추가한다. */
    @Test
    void replacesTagLinks() {
        UUID postId = service.create(command("태그 교체", "tag-replace", List.of("old", "keep"), PublishAction.KEEP), AUTHOR);

        service.update(postId, command("태그 교체", "tag-replace", List.of("keep", "new"), PublishAction.KEEP));
        entityManager.flush();

        assertThat(tagNames(postId)).containsExactlyInAnyOrder("keep", "new");
    }

    /** 시리즈에 속한 글은 수정 요청의 카테고리를 무시하고 시리즈 카테고리를 유지한다. */
    @Test
    void keepsSeriesCategory() {
        UUID postId = service.create(command("시리즈 글", "series-post", List.of(), PublishAction.KEEP), AUTHOR);
        UUID seriesId = seriesService.createSeries(new PostSeriesCommand(id(10), "editor-series", "시리즈", null, 0));
        seriesService.assignPost(seriesId, postId, 1);
        entityManager.flush();
        entityManager.clear();

        service.update(postId, new PostEditCommand("시리즈 글", "series-post", null, "본문", id(11), List.of(), null, PublishAction.KEEP));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT category_id FROM blog.post_detail WHERE id = ?", UUID.class, postId)).isEqualTo(id(10));
    }

    /** 대표 이미지는 이 글에 첨부된 파일만 지정할 수 있다. */
    @Test
    void acceptsOnlyAttachedRepresentativeImage() {
        UUID postId = service.create(command("대표 이미지", "cover-post", List.of(), PublishAction.KEEP), AUTHOR);
        UUID otherId = service.create(command("다른 글", "other-cover-post", List.of(), PublishAction.KEEP), AUTHOR);
        entityManager.flush();
        jdbc.update("INSERT INTO blog.attached_files (id, original_name, stored_name, storage_path) VALUES (?, 'a.png', 'a.png', 'posts/a.png')", id(40));
        jdbc.update("INSERT INTO blog.post_attached_file (post_detail_id, attached_file_id) VALUES (?, ?)", otherId, id(40));

        assertThatThrownBy(() -> service.update(postId, cover(id(40))))
                .isInstanceOf(IllegalArgumentException.class);

        jdbc.update("INSERT INTO blog.post_attached_file (post_detail_id, attached_file_id) VALUES (?, ?)", postId, id(40));
        service.update(postId, cover(id(40)));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT representative_image_id FROM blog.post_detail WHERE id = ?", UUID.class, postId)).isEqualTo(id(40));
    }

    /** 발행하면 발행 시각이 생기고, 비공개 전환 후 다시 발행해도 처음 발행 시각을 유지한다. */
    @Test
    void publishesAndUnpublishes() {
        UUID postId = service.create(command("발행", "publish-post", List.of(), PublishAction.PUBLISH), AUTHOR);
        entityManager.flush();
        Timestamp firstPublishedAt = jdbc.queryForObject("SELECT published_at FROM blog.post_detail WHERE id = ?", Timestamp.class, postId);
        assertThat(text("status", postId)).isEqualTo("PUBLISHED");
        assertThat(firstPublishedAt).isNotNull();

        service.update(postId, command("발행", "publish-post", List.of(), PublishAction.UNPUBLISH));
        entityManager.flush();
        assertThat(text("status", postId)).isEqualTo("DRAFT");

        service.update(postId, command("발행", "publish-post", List.of(), PublishAction.PUBLISH));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT published_at FROM blog.post_detail WHERE id = ?", Timestamp.class, postId))
                .isEqualTo(firstPublishedAt);
    }

    /**
     * @param title 제목
     * @param slug 주소
     * @param tags 태그 이름
     * @param action 공개 상태 동작
     * @return 카테고리 10번에 넣는 작성 명령
     */
    private PostEditCommand command(String title, String slug, List<String> tags, PublishAction action) {
        return new PostEditCommand(title, slug, null, "본문", id(10), tags, null, action);
    }

    /** @param imageId 대표 이미지 식별자 @return 대표 이미지만 지정하는 수정 명령 */
    private PostEditCommand cover(UUID imageId) {
        return new PostEditCommand("대표 이미지", "cover-post", null, "본문", id(10), List.of(), imageId, PublishAction.KEEP);
    }

    /** @param column 컬럼 이름 @param postId 게시글 식별자 @return 문자열 컬럼 값 */
    private String text(String column, UUID postId) {
        return jdbc.queryForObject("SELECT " + column + " FROM blog.post_detail WHERE id = ?", String.class, postId);
    }

    /** @param postId 게시글 식별자 @return 연결된 태그 이름을 이름순으로 나열한 목록 */
    private List<String> tagNames(UUID postId) {
        return jdbc.queryForList("SELECT t.name FROM blog.post_tag pt JOIN blog.tag t ON t.id = pt.tag_id WHERE pt.post_detail_id = ? ORDER BY t.name",
                String.class, postId);
    }

    /** @param number 식별자 숫자 @return 고정 테스트 UUID */
    private UUID id(int number) {
        return new UUID(0, number);
    }
}
