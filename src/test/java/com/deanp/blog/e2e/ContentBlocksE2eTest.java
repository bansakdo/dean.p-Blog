package com.deanp.blog.e2e;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 편집기의 코드 블록·Mermaid 넣기 도구, Archify 다이어그램 업로드, 미리보기와 공개 화면 표시를 브라우저로 검증한다. */
class ContentBlocksE2eTest extends E2eTestSupport {

    /** 넣기 도구로 만든 코드·Mermaid 블록과 올린 다이어그램이 미리보기와 공개 화면에 그려지고, 다이어그램은 격리된다. */
    @Test
    void writesCodeMermaidAndArchifyBlocks() throws IOException {
        String slug = "e2e-blocks-" + stamp;
        login();
        page.navigate("/admin/posts/new");
        page.fill("#post-title", "E2E 블록 " + stamp);
        page.fill("#post-slug", slug);
        page.fill("#post-content", "소개 문단");
        submit("button[value=save]");
        String postId = idFromUrl();

        // 넣기 도구: 선택한 언어의 코드 블록과 Mermaid 틀을 커서 위치에 넣는다.
        moveCursorToEnd();
        page.selectOption("#post-code-language", "java");
        page.click("[data-insert='code']");
        page.keyboard().type("System.out.println(\"hi\");");
        moveCursorToEnd();
        page.click("[data-insert='mermaid']");
        String content = page.inputValue("#post-content");
        assertThat(content).contains("```java\nSystem.out.println(\"hi\");\n```", "```mermaid\nflowchart LR");

        // Archify가 아닌 HTML은 거부하고, Archify HTML은 올리면 본문에 블록이 들어가며 대표 이미지 후보에는 없다.
        Path plain = Files.createTempFile("plain", ".html");
        Files.writeString(plain, "<html><head><title>x</title></head></html>");
        page.setInputFiles("#post-diagram-upload", plain);
        expect(page.locator("[data-diagram-status]")).containsText("Archify로 만든 HTML이 아닙니다");
        moveCursorToEnd();
        page.setInputFiles("#post-diagram-upload", Paths.get("src/test/resources/e2e/archify-sample.html"));
        expect(page.locator("[data-diagram-status]")).hasText("본문에 다이어그램을 넣었습니다. 저장해야 반영됩니다.");
        assertThat(page.inputValue("#post-content")).containsPattern("```archify\\n[0-9a-f-]{36}\\n```");
        expect(page.locator("[data-diagram-list] li")).hasCount(1);
        expect(page.locator("#post-representative-image option")).hasCount(1);

        // 첨부 파일: 허용하지 않은 형식은 거부하고, 올리면 본문에 내려받기 링크가 들어가며, 목록에서 지울 수 있다(확인 창은 공통 설정이 수락).
        // 공개 전에는 로그인한 작성자만 받을 수 있다.
        Path executable = Files.createTempFile("setup", ".exe");
        page.setInputFiles("#post-file-upload", executable);
        expect(page.locator("[data-file-status]")).containsText("올릴 수 없는 파일 형식");
        Path notes = Files.writeString(Files.createTempFile("notes", ".txt"), "첨부 내용");
        moveCursorToEnd();
        page.setInputFiles("#post-file-upload", notes);
        expect(page.locator("[data-file-status]")).hasText("본문에 첨부 파일 링크를 넣었습니다. 저장해야 반영됩니다.");
        assertThat(page.inputValue("#post-content")).containsPattern("\\[notes[0-9]*\\.txt]\\(/media/files/[0-9a-f-]{36}\\)");
        String fileUrl = page.locator("[data-file-list] a").first().getAttribute("href");
        assertThat(status(fileUrl)).isEqualTo(200);
        assertThat(anonymousStatus(fileUrl)).isEqualTo(404);
        page.setInputFiles("#post-file-upload", Files.writeString(Files.createTempFile("extra", ".md"), "# 지울 파일"));
        expect(page.locator("[data-file-list] li")).hasCount(2);
        page.click("[data-file-list] li:nth-child(2) [data-delete-file]");
        expect(page.locator("[data-file-list] li")).hasCount(1);
        expect(page.locator("#post-representative-image option")).hasCount(1);

        // 미리보기: 저장 전 본문을 공개 화면처럼 그린다.
        page.click("[data-preview-toggle]");
        expect(page.locator("[data-preview] .code-language")).hasText("java");
        expect(page.locator("[data-preview] pre.mermaid svg")).isVisible();
        expect(page.frameLocator("[data-preview] iframe").locator("#state")).containsText("archify-ok");

        page.fill("#post-published-at", "");
        submit("button[value=publish]");
        expect(page.locator(".admin-heading .admin-badge")).hasText("공개");
        page.reload();
        expect(page.locator("[data-diagram-list] li")).hasCount(1);
        expect(page.locator("[data-file-list] li")).hasCount(1);
        expect(page.locator("#post-representative-image option")).hasCount(1);

        // 공개 화면: 코드 강조·복사 버튼, Mermaid 그림, 격리된 Archify iframe(부모·쿠키 접근 불가, 테마 전달).
        Page publicPage = context.newPage();
        publicPage.navigate("/posts/" + slug);
        expect(publicPage.locator(".code-block code.hljs")).containsText("System.out.println");
        expect(publicPage.locator(".code-block .code-copy")).hasText("복사");
        expect(publicPage.locator("pre.mermaid svg")).isVisible();
        FrameLocator diagram = publicPage.frameLocator(".archify-embed iframe");
        expect(diagram.locator("#state")).hasText(java.util.regex.Pattern.compile("archify-ok parent-blocked cookie-blocked theme=(light|dark)"));
        assertThat(publicPage.locator(".archify-embed iframe").getAttribute("sandbox")).doesNotContain("allow-same-origin");
        expect(publicPage.locator(".article-body a[href='" + fileUrl + "']")).hasCount(1);
        expect(publicPage.locator(".article-attachments li")).hasCount(1);
        expect(publicPage.locator(".article-attachments a")).hasAttribute("href", fileUrl);
        assertThat(anonymousStatus(fileUrl)).isEqualTo(200);

        String diagramUrl = publicPage.locator(".archify-embed a").getAttribute("href");
        var response = context.request().get(diagramUrl);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().get("content-security-policy")).startsWith("sandbox allow-scripts");
        assertThat(status("/media/diagrams/" + java.util.UUID.randomUUID())).isEqualTo(404);

        assertNoMobileOverflow("/posts/" + slug, "/admin/posts/" + postId + "/edit");
        publicPage.close();
        assertNoConsoleErrors("status of 400");
    }

    /** 본문 입력칸의 커서를 맨 끝으로 옮긴다. */
    private void moveCursorToEnd() {
        page.evaluate("() => { const t = document.querySelector('#post-content'); t.focus(); t.setSelectionRange(t.value.length, t.value.length); }");
    }
}
