package com.deanp.blog.post.service;

import com.deanp.blog.persistence.TestPostgresql;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.service.PostEditorService.AdminPostRow;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        assertThatThrownBy(() -> service.update(second, command("둘", "same-slug", List.of(), PublishAction.KEEP), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);

        // 자기 주소를 그대로 저장하는 것은 허용한다.
        service.update(first, command("하나 수정", "same-slug", List.of(), PublishAction.KEEP), AUTHOR);
        entityManager.flush();
        assertThat(text("title", first)).isEqualTo("하나 수정");
    }

    /** 태그 수정은 빠진 연결을 지우고 새 연결만 추가한다. */
    @Test
    void replacesTagLinks() {
        UUID postId = service.create(command("태그 교체", "tag-replace", List.of("old", "keep"), PublishAction.KEEP), AUTHOR);

        service.update(postId, command("태그 교체", "tag-replace", List.of("keep", "new"), PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        assertThat(tagNames(postId)).containsExactlyInAnyOrder("keep", "new");
    }

    /** 시리즈에 속한 글은 수정 요청의 카테고리를 무시하고 시리즈 카테고리를 유지한다. */
    @Test
    void keepsSeriesCategory() {
        UUID postId = service.create(command("시리즈 글", "series-post", List.of(), PublishAction.KEEP), AUTHOR);
        UUID seriesId = seriesService.createSeries(new PostSeriesCommand(id(10), "editor-series", "시리즈", null, 0, null));
        seriesService.assignPost(seriesId, postId, 1);
        entityManager.flush();
        entityManager.clear();

        service.update(postId, new PostEditCommand("시리즈 글", "series-post", null, "본문", id(11), List.of(), null, PublishAction.KEEP, null), AUTHOR);
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

        assertThatThrownBy(() -> service.update(postId, cover(id(40)), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);

        jdbc.update("INSERT INTO blog.post_attached_file (post_detail_id, attached_file_id) VALUES (?, ?)", postId, id(40));
        service.update(postId, cover(id(40)), AUTHOR);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT representative_image_id FROM blog.post_detail WHERE id = ?", UUID.class, postId)).isEqualTo(id(40));
    }

    /** 같은 글에 첨부했더라도 Archify 다이어그램 같은 이미지가 아닌 첨부는 대표 이미지로 지정할 수 없다. */
    @Test
    void rejectsDiagramAsRepresentativeImage() {
        UUID postId = service.create(command("대표 이미지", "cover-post", List.of(), PublishAction.KEEP), AUTHOR);
        entityManager.flush();
        jdbc.update("INSERT INTO blog.attached_files (id, original_name, stored_name, storage_path, content_type) VALUES (?, 'd.html', 'd.html', 'diagrams/d.html', 'text/html')", id(41));
        jdbc.update("INSERT INTO blog.post_attached_file (post_detail_id, attached_file_id) VALUES (?, ?)", postId, id(41));

        assertThatThrownBy(() -> service.update(postId, cover(id(41)), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 발행하면 발행 시각이 생기고, 비공개 전환 후 다시 발행해도 처음 발행 시각을 유지한다. */
    @Test
    void publishesAndUnpublishes() {
        UUID postId = service.create(command("발행", "publish-post", List.of(), PublishAction.PUBLISH), AUTHOR);
        entityManager.flush();
        Timestamp firstPublishedAt = jdbc.queryForObject("SELECT published_at FROM blog.post_detail WHERE id = ?", Timestamp.class, postId);
        assertThat(text("status", postId)).isEqualTo("PUBLISHED");
        assertThat(firstPublishedAt).isNotNull();

        service.update(postId, command("발행", "publish-post", List.of(), PublishAction.UNPUBLISH), AUTHOR);
        entityManager.flush();
        assertThat(text("status", postId)).isEqualTo("DRAFT");

        service.update(postId, command("발행", "publish-post", List.of(), PublishAction.PUBLISH), AUTHOR);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT published_at FROM blog.post_detail WHERE id = ?", Timestamp.class, postId))
                .isEqualTo(firstPublishedAt);
    }

    /** 삭제하면 공개 화면과 기본 목록에서 빠지고, 복구하면 공개되지 않은 초안으로 돌아온다. */
    @Test
    void deletesAndRestores() {
        UUID postId = service.create(command("삭제", "delete-post", List.of("남는 태그"), PublishAction.PUBLISH), AUTHOR);
        entityManager.flush();
        assertThat(posts.findPublishedRowsBySlug("delete-post")).isNotEmpty();

        // 다음 요청처럼 새로 읽도록 영속성 컨텍스트를 비운다.
        service.delete(postId, AUTHOR);
        entityManager.flush();
        entityManager.clear();
        assertThat(text("status", postId)).isEqualTo("DELETED");
        assertThat(posts.findPublishedRowsBySlug("delete-post")).isEmpty();
        assertThat(titles(AdminPostFilter.ALL, "")).doesNotContain("삭제");
        assertThat(titles(AdminPostFilter.DELETED, "")).containsExactly("삭제");
        assertThat(service.findForEdit(postId).orElseThrow().deleted()).isTrue();
        assertThat(tagNames(postId)).containsExactly("남는 태그");

        service.restore(postId, AUTHOR);
        entityManager.flush();
        assertThat(text("status", postId)).isEqualTo("DRAFT");
        assertThat(posts.findPublishedRowsBySlug("delete-post")).isEmpty();
    }

    /** 삭제한 글은 복구하기 전에는 고칠 수 없고, 삭제하지 않은 글은 복구할 수 없다. */
    @Test
    void guardsDeletedState() {
        UUID postId = service.create(command("보호", "guard-post", List.of(), PublishAction.KEEP), AUTHOR);
        assertThatThrownBy(() -> service.restore(postId, AUTHOR)).isInstanceOf(IllegalArgumentException.class);

        service.delete(postId, AUTHOR);
        assertThatThrownBy(() -> service.update(postId, command("고침", "guard-post", List.of(), PublishAction.PUBLISH), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);
        entityManager.flush();
        assertThat(text("title", postId)).isEqualTo("보호");
        assertThat(text("status", postId)).isEqualTo("DELETED");
    }

    /** 삭제한 글의 주소는 새 글이 다시 쓸 수 있고, 그 뒤에는 겹치는 옛 글을 복구하지 않는다. */
    @Test
    void reusesSlugOfDeletedPostAndBlocksConflictingRestore() {
        UUID oldId = service.create(command("옛 글", "reused-slug", List.of(), PublishAction.PUBLISH), AUTHOR);
        service.delete(oldId, AUTHOR);
        entityManager.flush();

        UUID newId = service.create(command("새 글", "reused-slug", List.of(), PublishAction.PUBLISH), AUTHOR);
        entityManager.flush();
        assertThat(posts.findPublishedRowsBySlug("reused-slug")).extracting(row -> row.title()).containsOnly("새 글");
        assertThatThrownBy(() -> service.create(command("또 새 글", "reused-slug", List.of(), PublishAction.KEEP), AUTHOR))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.restore(oldId, AUTHOR)).isInstanceOf(PostEditorService.SlugConflictException.class);
        entityManager.flush();
        assertThat(text("status", oldId)).isEqualTo("DELETED");

        // 새 글의 주소를 바꾸면 옛 글을 복구할 수 있다.
        service.update(newId, command("새 글", "renamed-slug", List.of(), PublishAction.KEEP), AUTHOR);
        service.restore(oldId, AUTHOR);
        entityManager.flush();
        assertThat(text("status", oldId)).isEqualTo("DRAFT");
    }

    /** DB 인덱스도 삭제한 글끼리는 같은 주소를 허용하고, 삭제하지 않은 글끼리는 막는다. */
    @Test
    void enforcesSlugUniquenessOnlyAmongActivePosts() {
        String insert = "INSERT INTO blog.post_detail (id, author_id, slug, title, content, status) VALUES (?, ?, 'db-slug', '제목', '본문', ?)";
        jdbc.update(insert, id(50), id(1), "DELETED");
        jdbc.update(insert, id(51), id(1), "DELETED");
        jdbc.update(insert, id(52), id(1), "DRAFT");

        assertThatThrownBy(() -> jdbc.update(insert, id(53), id(1), "PUBLISHED"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** 상태 필터와 제목 검색어를 함께 적용하고, 검색어는 대소문자와 앞뒤 공백, LIKE 특수 문자를 그대로 다룬다. */
    @Test
    void filtersByStatusAndTitle() {
        service.create(command("Spring 초안", "filter-draft", List.of(), PublishAction.KEEP), AUTHOR);
        service.create(command("spring 공개", "filter-published", List.of(), PublishAction.PUBLISH), AUTHOR);
        service.create(command("100% 일상", "filter-percent", List.of(), PublishAction.KEEP), AUTHOR);
        service.create(command("snake_case 정리", "filter-underscore", List.of(), PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        assertThat(titles(AdminPostFilter.ALL, " SPRING ")).containsExactlyInAnyOrder("Spring 초안", "spring 공개");
        assertThat(titles(AdminPostFilter.DRAFT, "spring")).containsExactly("Spring 초안");
        assertThat(titles(AdminPostFilter.PUBLISHED, "")).containsExactly("spring 공개");
        assertThat(titles(AdminPostFilter.ALL, "%")).containsExactly("100% 일상");
        assertThat(titles(AdminPostFilter.ALL, "_")).containsExactly("snake_case 정리");
    }

    /** 대시보드 현황은 예약 글을 공개 글과 따로 세고, 곧 발행할 예약 글과 최근에 고친 글(삭제한 글 제외)을 보여 준다. */
    @Test
    void summarizesPostsForDashboard() {
        PostEditorService.PostSummary before = service.summarize();
        service.create(command("현황 초안", "summary-draft", List.of(), PublishAction.KEEP), AUTHOR);
        service.create(command("현황 공개", "summary-published", List.of(), PublishAction.PUBLISH), AUTHOR);
        UUID deleted = service.create(command("현황 삭제", "summary-deleted", List.of(), PublishAction.KEEP), AUTHOR);
        service.delete(deleted, AUTHOR);
        // 다른 예약 글보다 먼저 발행되도록 가까운 미래로 잡아 곧 발행할 다섯 편에 들게 한다.
        Instant future = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        service.create(scheduled("현황 예약", "summary-scheduled", PublishAction.PUBLISH, future), AUTHOR);
        entityManager.flush();

        PostEditorService.PostSummary after = service.summarize();
        assertThat(after.published() - before.published()).isEqualTo(1);
        assertThat(after.scheduled() - before.scheduled()).isEqualTo(1);
        assertThat(after.drafts() - before.drafts()).isEqualTo(1);
        assertThat(after.deleted() - before.deleted()).isEqualTo(1);
        assertThat(after.upcoming()).extracting(AdminPostRow::title).contains("현황 예약");
        assertThat(after.recent()).extracting(AdminPostRow::title)
                .contains("현황 초안", "현황 공개", "현황 예약").doesNotContain("현황 삭제");
    }

    /** 미래 시각으로 발행하면 그 시각 전까지 공개 화면에 나오지 않고, 관리 화면에는 예약으로 보인다. */
    @Test
    void schedulesFuturePublish() {
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        UUID postId = service.create(scheduled("예약", "scheduled-post", PublishAction.PUBLISH, future), AUTHOR);
        entityManager.flush();

        assertThat(text("status", postId)).isEqualTo("PUBLISHED");
        assertThat(publishedAt(postId)).isEqualTo(future);
        assertThat(posts.findPublishedRowsBySlug("scheduled-post")).isEmpty();
        assertThat(service.findForEdit(postId).orElseThrow().scheduled()).isTrue();
        assertThat(service.findPage(0, AdminPostFilter.PUBLISHED, "예약").getContent().getFirst().scheduled()).isTrue();

        // 공개 중인 글은 저장만으로 발행 시각을 바꾸고, 과거로 옮기면 바로 공개된다.
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        service.update(postId, scheduled("예약", "scheduled-post", PublishAction.KEEP, past), AUTHOR);
        entityManager.flush();
        assertThat(publishedAt(postId)).isEqualTo(past);
        assertThat(posts.findPublishedRowsBySlug("scheduled-post")).isNotEmpty();
    }

    /** 예약을 취소한 뒤 시각 없이 다시 발행하면 남아 있던 미래 시각 대신 지금 공개한다. */
    @Test
    void republishesNowAfterCancelingSchedule() {
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        UUID postId = service.create(scheduled("취소", "cancel-post", PublishAction.PUBLISH, future), AUTHOR);
        service.update(postId, scheduled("취소", "cancel-post", PublishAction.UNPUBLISH, null), AUTHOR);
        service.update(postId, scheduled("취소", "cancel-post", PublishAction.PUBLISH, null), AUTHOR);
        entityManager.flush();

        assertThat(publishedAt(postId)).isBefore(Instant.now().plusSeconds(1));
        assertThat(posts.findPublishedRowsBySlug("cancel-post")).isNotEmpty();
    }

    /** 폼이 분 단위로 다시 보낸 기존 발행 시각은 초 단위 원래 값을 바꾸지 않는다. */
    @Test
    void keepsOriginalPublishTimeWithinSameMinute() {
        UUID postId = service.create(command("원래", "same-minute", List.of(), PublishAction.PUBLISH), AUTHOR);
        entityManager.flush();
        Instant original = publishedAt(postId);

        service.update(postId, scheduled("원래", "same-minute", PublishAction.KEEP, original.truncatedTo(ChronoUnit.MINUTES)), AUTHOR);
        entityManager.flush();
        assertThat(publishedAt(postId)).isEqualTo(original);
    }

    /** @param postId 게시글 식별자 @return 저장된 발행 시각 */
    private Instant publishedAt(UUID postId) {
        return jdbc.queryForObject("SELECT published_at FROM blog.post_detail WHERE id = ?", Timestamp.class, postId).toInstant();
    }

    /**
     * @param title 제목
     * @param slug 주소
     * @param action 공개 상태 동작
     * @param publishAt 발행 시각 또는 null
     * @return 발행 시각을 지정한 작성·수정 명령
     */
    private PostEditCommand scheduled(String title, String slug, PublishAction action, Instant publishAt) {
        return new PostEditCommand(title, slug, null, "본문", id(10), List.of(), null, action, publishAt);
    }

    /** @param filter 상태 필터 @param query 제목 검색어 @return 첫 페이지 글 제목 */
    private List<String> titles(AdminPostFilter filter, String query) {
        return service.findPage(0, filter, query).map(AdminPostRow::title).getContent();
    }

    /**
     * @param title 제목
     * @param slug 주소
     * @param tags 태그 이름
     * @param action 공개 상태 동작
     * @return 카테고리 10번에 넣는 작성 명령
     */
    private PostEditCommand command(String title, String slug, List<String> tags, PublishAction action) {
        return new PostEditCommand(title, slug, null, "본문", id(10), tags, null, action, null);
    }

    /** @param imageId 대표 이미지 식별자 @return 대표 이미지만 지정하는 수정 명령 */
    private PostEditCommand cover(UUID imageId) {
        return new PostEditCommand("대표 이미지", "cover-post", null, "본문", id(10), List.of(), imageId, PublishAction.KEEP, null);
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
