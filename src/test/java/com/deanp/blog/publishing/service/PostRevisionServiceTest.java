package com.deanp.blog.publishing.service;

import com.deanp.blog.persistence.TestPostgresql;
import com.deanp.blog.post.service.PostEditCommand;
import com.deanp.blog.post.service.PostEditCommand.PublishAction;
import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.publishing.service.PostRevisionService.RevisionRow;
import com.deanp.blog.publishing.service.PostRevisionService.RevisionView;
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

/** 격리된 PostgreSQL에서 글 변경 이력의 기록·조회·되돌림 규칙을 검증한다. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PostRevisionServiceTest {

    private static final TestPostgresql DB = new TestPostgresql();
    private static final String AUTHOR = "revision-user";

    @Autowired
    private PostRevisionService service;
    @Autowired
    private PostEditorService editor;
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
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, ?, 'hash', '작성자')", id(1), AUTHOR);
    }

    /** 작성·저장·발행·비공개·삭제·복구마다 버전이 늘고, 내용이 같은 저장은 건너뛴다. */
    @Test
    void recordsEachChangeAndSkipsUnchangedSave() {
        UUID postId = editor.create(command("처음", "본문 1", PublishAction.KEEP), AUTHOR);
        editor.update(postId, command("둘째", "본문 2", PublishAction.KEEP), AUTHOR);
        editor.update(postId, command("둘째", "본문 2", PublishAction.KEEP), AUTHOR);
        editor.update(postId, command("둘째", "본문 2", PublishAction.PUBLISH), AUTHOR);
        editor.update(postId, command("둘째", "본문 2", PublishAction.UNPUBLISH), AUTHOR);
        editor.delete(postId, AUTHOR);
        editor.restore(postId, AUTHOR);
        // 다음 요청처럼 변경한 사용자를 DB에서 다시 읽도록 영속성 컨텍스트를 비운다.
        entityManager.flush();
        entityManager.clear();

        List<RevisionRow> history = service.findHistory(postId);
        assertThat(history).extracting(RevisionRow::revisionNo).containsExactly(6, 5, 4, 3, 2, 1);
        assertThat(history).extracting(RevisionRow::changeType)
                .containsExactly("복구", "삭제", "비공개 전환", "발행", "저장", "작성");
        assertThat(history).extracting(RevisionRow::changedBy).containsOnly(AUTHOR);
    }

    /** 이력 기능 전에 만든 글은 처음 고칠 때 고치기 전 상태를 첫 버전으로 남긴다. */
    @Test
    void recordsBaselineForPostWithoutHistory() {
        jdbc.update("INSERT INTO blog.post_detail (id, author_id, slug, title, content, status) VALUES (?, ?, 'legacy', '옛 제목', '옛 본문', 'DRAFT')",
                id(20), id(1));

        editor.update(id(20), command("새 제목", "새 본문", PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        List<RevisionRow> history = service.findHistory(id(20));
        assertThat(history).extracting(RevisionRow::changeType).containsExactly("저장", "기록 시작 전");
        assertThat(history.get(1).changedBy()).isNull();
        assertThat(service.find(id(20), 1).orElseThrow().content()).isEqualTo("옛 본문");
    }

    /** 이전 버전으로 되돌리면 제목·요약·본문만 바뀌고 공개 상태와 주소는 그대로이며, 되돌린 결과도 버전으로 남는다. */
    @Test
    void revertsContentOnly() {
        UUID postId = editor.create(command("처음", "본문 1", PublishAction.PUBLISH), AUTHOR);
        editor.update(postId, command("둘째", "본문 2", PublishAction.KEEP), AUTHOR);
        entityManager.flush();

        RevisionView first = service.find(postId, 1).orElseThrow();
        assertThat(first.titleDiffers()).isTrue();
        assertThat(first.contentDiffers()).isTrue();
        assertThat(first.summaryDiffers()).isFalse();

        service.revert(postId, 1, AUTHOR);
        entityManager.flush();
        assertThat(text("title", postId)).isEqualTo("처음");
        assertThat(text("content", postId)).isEqualTo("본문 1");
        assertThat(text("status", postId)).isEqualTo("PUBLISHED");
        assertThat(text("slug", postId)).isEqualTo("revision-post");
        assertThat(service.findHistory(postId).getFirst().changeType()).isEqualTo("되돌림");
        assertThat(service.find(postId, 1).orElseThrow().contentDiffers()).isFalse();
    }

    /** 삭제한 글과 없는 버전은 되돌리지 않고, 다른 글에만 있는 버전 번호로는 조회되지 않는다. */
    @Test
    void guardsRevert() {
        UUID postId = editor.create(command("처음", "본문 1", PublishAction.KEEP), AUTHOR);
        editor.update(postId, command("둘째", "본문 2", PublishAction.KEEP), AUTHOR);
        UUID otherId = editor.create(new PostEditCommand("다른 글", "other-post", null, "본문", null, List.of(), null,
                PublishAction.KEEP, null), AUTHOR);
        assertThat(service.find(otherId, 2)).isEmpty();
        assertThatThrownBy(() -> service.revert(otherId, 2, AUTHOR)).isInstanceOf(IllegalArgumentException.class);

        editor.delete(postId, AUTHOR);
        assertThatThrownBy(() -> service.revert(postId, 1, AUTHOR)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.find(id(99), 1)).isEmpty();
    }

    /** 공개 상태가 바뀌지 않은 발행·비공개 요청과 현재와 같은 버전으로의 되돌림은 새 버전을 남기지 않는다. */
    @Test
    void skipsChangesWithoutEffect() {
        UUID postId = editor.create(command("처음", "본문 1", PublishAction.PUBLISH), AUTHOR);
        editor.update(postId, command("처음", "본문 1", PublishAction.PUBLISH), AUTHOR);
        editor.update(postId, command("처음", "본문 1", PublishAction.UNPUBLISH), AUTHOR);
        editor.update(postId, command("처음", "본문 1", PublishAction.UNPUBLISH), AUTHOR);
        service.revert(postId, 2, AUTHOR);
        entityManager.flush();

        assertThat(service.findHistory(postId)).extracting(RevisionRow::changeType).containsExactly("비공개 전환", "발행");
    }

    /** 이력 기능 전에 만든 글을 바로 삭제해도 삭제 전 상태가 첫 버전으로 남는다. */
    @Test
    void recordsBaselineBeforeDeletingLegacyPost() {
        jdbc.update("INSERT INTO blog.post_detail (id, author_id, slug, title, content, status) VALUES (?, ?, 'legacy-delete', '옛 글', '옛 본문', 'PUBLISHED')",
                id(21), id(1));

        editor.delete(id(21), AUTHOR);
        entityManager.flush();

        assertThat(service.findHistory(id(21))).extracting(RevisionRow::changeType).containsExactly("삭제", "기록 시작 전");
    }

    /** 요약을 비우거나 채우는 것도 내용 변경으로 보고, 현재와 다른 항목에 표시한다. */
    @Test
    void treatsSummaryChangesAsContentChanges() {
        UUID postId = editor.create(command("처음", "본문", PublishAction.KEEP), AUTHOR);
        editor.update(postId, new PostEditCommand("처음", "revision-post", "요약", "본문", null, List.of(), null,
                PublishAction.KEEP, null), AUTHOR);
        entityManager.flush();

        assertThat(service.findHistory(postId)).hasSize(2);
        RevisionView first = service.find(postId, 1).orElseThrow();
        assertThat(first.summaryDiffers()).isTrue();
        assertThat(first.titleDiffers()).isFalse();
    }

    /**
     * @param title 제목
     * @param content 본문
     * @param action 공개 상태 동작
     * @return 같은 주소를 쓰는 글 작성·수정 명령
     */
    private static PostEditCommand command(String title, String content, PublishAction action) {
        return new PostEditCommand(title, "revision-post", null, content, null, List.of(), null, action, null);
    }

    /** @param column 컬럼 이름 @param postId 게시글 식별자 @return 게시글의 문자열 컬럼 값 */
    private String text(String column, UUID postId) {
        return jdbc.queryForObject("SELECT " + column + " FROM blog.post_detail WHERE id = ?", String.class, postId);
    }

    /** @param value 식별자 끝자리 @return 테스트용 고정 UUID */
    private static UUID id(int value) {
        return new UUID(0, value);
    }
}
