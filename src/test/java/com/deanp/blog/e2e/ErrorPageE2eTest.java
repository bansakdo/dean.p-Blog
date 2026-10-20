package com.deanp.blog.e2e;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

/** 없는 주소를 열면 블로그 레이아웃의 오류 화면에서 다른 곳으로 이동할 수 있는지 브라우저로 검증한다. */
class ErrorPageE2eTest extends E2eTestSupport {

    /** 없는 주소는 안내와 이동 링크가 있는 오류 화면을 보여 주고, 링크로 글 목록에 가며, 모바일에서도 넘치지 않는다. */
    @Test
    void showsNotFoundPageWithNavigation() {
        page.navigate("/no-such-page");

        expect(page.locator("h1")).hasText("페이지를 찾을 수 없습니다");
        expect(page.locator(".site-header")).isVisible();
        page.locator(".error-links a", new Page.LocatorOptions().setHasText("글 목록")).click();
        waitForUrl(".*/dev$");

        assertNoMobileOverflow("/no-such-page", "/posts/no-such-post");
        assertNoConsoleErrors("status of 404");
    }
}
