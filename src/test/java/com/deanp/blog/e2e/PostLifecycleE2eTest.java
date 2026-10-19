package com.deanp.blog.e2e;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 글 작성부터 발행, 변경 이력과 되돌리기, 예약 발행, 삭제와 복구까지 브라우저로 검증한다. */
class PostLifecycleE2eTest extends E2eTestSupport {

    /** 로그인하지 않으면 관리 화면 대신 로그인 화면으로 보낸다. */
    @Test
    void redirectsAnonymousAdminToLogin() {
        page.navigate("/admin/posts");

        waitForUrl(".*/login$");
        expect(page.locator("#username")).isVisible();
    }

    /** 초안은 숨기고, 발행하면 공개하며, 이력에서 되돌리고, 삭제·복구해도 공개되지 않는다. */
    @Test
    void writesPublishesRevertsDeletesAndRestoresPost() {
        String slug = "e2e-post-" + stamp;
        login();

        // 작성: 초안으로 저장하면 이력에 '작성'만 남고 공개 화면에는 나오지 않는다.
        page.navigate("/admin/posts/new");
        page.fill("#post-title", "E2E 글 v1 " + stamp);
        page.fill("#post-slug", slug);
        page.fill("#post-content", "첫 번째 본문 <script>alert(1)</script>");
        submit("button[value=save]");
        waitForUrl(".*/admin/posts/[^/]+/edit.*");
        String postId = idFromUrl();
        assertThat(historyTypes()).containsExactly("작성");
        assertThat(status("/posts/" + slug)).isEqualTo(404);

        // 수정해 저장하면 '저장'이 남고, 같은 내용으로 다시 저장하면 늘지 않으며, 발행하면 공개된다.
        page.fill("#post-title", "E2E 글 v2 " + stamp);
        page.fill("#post-content", "두 번째 본문");
        submit("button[value=save]");
        assertThat(historyTypes()).containsExactly("저장", "작성");
        submit("button[value=save]");
        assertThat(historyTypes()).containsExactly("저장", "작성");
        submit("button[value=publish]");
        expect(page.locator(".admin-heading .admin-badge")).hasText("공개");
        assertThat(historyTypes()).containsExactly("발행", "저장", "작성");
        assertThat(page.textContent(".admin-revisions tbody tr td:nth-child(4)").strip()).isEqualTo(ADMIN_LOGIN_ID);
        assertThat(status("/posts/" + slug)).isEqualTo(200);

        // 버전 1은 본문의 스크립트를 글자로만 보여 주고, 되돌리면 공개 상태를 유지한 채 제목이 바뀐다.
        page.click(".admin-revisions a:text('버전 1')");
        waitForUrl(".*/revisions/1$");
        expect(page.locator(".admin-revision-content")).containsText("<script>alert(1)</script>");
        page.click("button:text('이 버전으로 되돌리기')");
        waitForUrl(".*reverted=1.*");
        expect(page.locator("text=버전 1의 제목·요약·본문으로 되돌렸습니다.")).isVisible();
        assertThat(page.inputValue("#post-title")).isEqualTo("E2E 글 v1 " + stamp);
        expect(page.locator(".admin-heading .admin-badge")).hasText("공개");
        assertThat(historyTypes().getFirst()).isEqualTo("되돌림");
        var publicPage = context.newPage();
        publicPage.navigate("/posts/" + slug);
        expect(publicPage.locator("h1")).containsText("v1");
        publicPage.close();

        // 삭제하면 공개 화면과 기본 목록에서 사라지고, 삭제 목록에만 남는다.
        submit(".admin-danger-zone button");
        waitForUrl(".*/admin/posts\\?deleted.*");
        assertThat(status("/posts/" + slug)).isEqualTo(404);
        page.navigate("/admin/posts?status=deleted&q=" + stamp);
        expect(page.locator(".admin-table .admin-badge")).hasText("삭제됨");

        // 삭제한 글은 수정할 수 없고, 복구하면 초안이 되어 여전히 공개되지 않는다.
        page.navigate("/admin/posts/" + postId + "/edit");
        assertThat(page.isDisabled("#post-title")).isTrue();
        assertThat(page.isVisible("button:text('이 버전으로 되돌리기')")).isFalse();
        page.click(".admin-deleted button");
        waitForUrl(".*restored.*");
        expect(page.locator(".admin-heading .admin-badge")).hasText("초안");
        assertThat(status("/posts/" + slug)).isEqualTo(404);
        assertThat(status("/admin/posts/" + postId + "/revisions/999")).isEqualTo(404);

        assertNoMobileOverflow("/admin/posts?q=" + stamp, "/admin/posts/" + postId + "/edit",
                "/admin/posts/" + postId + "/revisions/1");
        assertNoConsoleErrors();
    }

    /** 미래 시각으로 발행하면 예약 상태로 숨겨 두고, 시각을 과거로 바꾸면 바로 공개한다. */
    @Test
    void schedulesAndReschedulesPublication() {
        String slug = "e2e-scheduled-" + stamp;
        login();

        page.navigate("/admin/posts/new");
        page.fill("#post-title", "E2E 예약 " + stamp);
        page.fill("#post-slug", slug);
        page.fill("#post-content", "예약 본문");
        page.fill("#post-published-at", localInput(Instant.now().plus(Duration.ofDays(2))));
        expect(page.locator("[data-publish-button]")).hasText("예약 발행");
        submit("button[value=publish]");
        expect(page.locator(".admin-heading .admin-badge")).hasText("예약");
        expect(page.locator("button[value=unpublish]")).hasText("예약 취소");
        assertThat(status("/posts/" + slug)).isEqualTo(404);

        page.fill("#post-published-at", localInput(Instant.now().minus(Duration.ofHours(1))));
        submit("button[value=save]");
        expect(page.locator(".admin-heading .admin-badge")).hasText("공개");
        assertThat(status("/posts/" + slug)).isEqualTo(200);
        assertNoConsoleErrors();
    }

    /**
     * 변경 이력 표의 종류 열을 읽는다.
     *
     * @return 최신순 종류 이름
     */
    private List<String> historyTypes() {
        return page.locator(".admin-revisions tbody tr td:nth-child(2)").allTextContents().stream()
                .map(String::strip).toList();
    }
}
