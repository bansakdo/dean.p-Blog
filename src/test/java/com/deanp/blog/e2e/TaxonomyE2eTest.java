package com.deanp.blog.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 카테고리·시리즈·태그 관리 화면에서 분류를 만들고 바꾸고 지우는 흐름을 브라우저로 검증한다. */
class TaxonomyE2eTest extends E2eTestSupport {

    /** 카테고리 순서와 중복 거부, 시리즈 구성, 태그 이름 변경과 병합, 쓰는 중인 분류 삭제 거부를 확인한다. */
    @Test
    void managesCategoriesSeriesAndTags() {
        String categoryName = "E2E 분류 " + stamp;
        String categorySlug = "e2e-cat-" + stamp;
        String tagA = "e2e-tag-a-" + stamp;
        String tagB = "e2e-tag-b-" + stamp;
        login();

        // 카테고리: 가장 작은 순서로 추가하면 맨 위에 오고, 같은 이름은 거부한다.
        page.click(".admin-nav a:text('카테고리')");
        Locator form = page.locator("section form.admin-term-form");
        form.locator("input[name=name]").fill(categoryName);
        form.locator("input[name=slug]").fill(categorySlug);
        form.locator("input[name=sortOrder]").fill("-100");
        form.locator("button").click();
        waitForUrl(".*saved.*");
        assertThat(page.inputValue(".admin-terms li:first-child input[name=slug]")).isEqualTo(categorySlug);
        form.locator("input[name=name]").fill(categoryName);
        form.locator("button").click();
        expect(page.locator(".admin-error")).isVisible();

        // 이 카테고리와 태그로 공개 글을 하나 쓰면 공개 화면 카테고리 목록 맨 앞에 나온다.
        page.navigate("/admin/posts/new");
        page.fill("#post-title", "E2E 분류 글 " + stamp);
        page.fill("#post-slug", "e2e-taxonomy-" + stamp);
        page.fill("#post-tags", tagA + ", " + tagB);
        page.selectOption("#post-category", new SelectOption().setLabel(categoryName));
        page.fill("#post-content", "분류 본문");
        page.click("button[value=publish]");
        expect(page.locator(".admin-heading .admin-badge")).hasText("공개");
        String postId = idFromUrl();
        Page publicPage = context.newPage();
        publicPage.navigate("/posts");
        assertThat(publicPage.locator("a[href*='category=']").first().textContent()).contains(categoryName);
        publicPage.close();

        // 시리즈: 만들고 글을 넣어 순서를 바꾸며, 글이 있는 동안은 지울 수 없다.
        page.navigate("/admin/series/new");
        page.fill("#series-name", "E2E 시리즈 " + stamp);
        page.selectOption("#series-category", new SelectOption().setLabel(categoryName));
        page.click("button[type=submit]:text('만들기')");
        waitForUrl(".*/edit\\?saved.*");
        page.selectOption("#series-add-post", postId);
        page.click("button:text('글 넣기')");
        expect(page.locator(".admin-member-title")).hasText("E2E 분류 글 " + stamp);
        page.fill(".admin-member-form input[name=seriesOrder]", "5");
        page.click(".admin-member-form button");
        expect(page.locator(".admin-member-form input[name=seriesOrder]")).hasValue("5");
        page.click("button.danger:text('시리즈 삭제')");
        expect(page.locator(".admin-error")).isVisible();
        assertNoMobileOverflow(page.url());
        page.click("button:text('시리즈에서 빼기')");
        expect(page.locator(".admin-member-form")).hasCount(0);
        page.click("button.danger:text('시리즈 삭제')");
        waitForUrl(".*/admin/series\\?deleted.*");

        // 태그: 이름을 바꾸면 주소도 다시 만들고, 병합하면 글의 태그가 대상 태그로 바뀐다.
        page.navigate("/admin/tags");
        page.click("a:text('" + tagA + "')");
        page.fill("#tag-name", tagA + "-renamed");
        page.fill("#tag-slug", "");
        page.click("button:text('저장')");
        expect(page.locator("#tag-slug")).hasValue(tagA + "-renamed");
        page.selectOption("#tag-merge-target", new SelectOption().setLabel(tagB + " (1편)"));
        page.click("button:text('병합')");
        waitForUrl(".*merged.*");
        page.navigate("/admin/posts/" + postId + "/edit");
        assertThat(page.inputValue("#post-tags")).isEqualTo(tagB);

        // 글이 쓰는 카테고리는 지울 수 없다.
        page.navigate("/admin/categories");
        page.locator(".admin-term", new Page.LocatorOptions().setHas(page.locator("input[value='" + categorySlug + "']")))
                .locator("button.admin-link-button").click();
        expect(page.locator(".admin-error")).isVisible();

        assertNoMobileOverflow("/admin/categories", "/admin/tags", "/admin/series", "/admin/posts");
        assertNoConsoleErrors();
    }
}
