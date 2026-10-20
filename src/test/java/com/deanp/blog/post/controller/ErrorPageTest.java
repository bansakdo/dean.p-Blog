package com.deanp.blog.post.controller;

import com.deanp.blog.persistence.TestPostgresql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 브라우저가 없는 주소나 없는 글을 열면 기본 오류 화면 대신 블로그 레이아웃의 오류 화면을 받는지 실제 서버로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ErrorPageTest {

    private static final TestPostgresql DB = new TestPostgresql();

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    /** @param registry 테스트 전용 데이터베이스와 임시 미디어 저장 위치 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        DB.register(registry);
        Path media = Files.createTempDirectory("blog-error-media");
        registry.add("APP_MEDIA_STORAGE_ROOT", media::toString);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    /** 매핑이 없는 주소와 없는 글 모두 404 상태로 헤더·사이드 메뉴·이동 링크가 있는 오류 화면을 그린다. */
    @Test
    void rendersNotFoundPageInSiteLayout() throws Exception {
        for (String path : new String[] {"/no-such-page", "/posts/no-such-post"}) {
            HttpResponse<String> response = get(path, "text/html");

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body())
                    .contains("<title>페이지를 찾을 수 없습니다 — dean.p</title>")
                    .contains("class=\"site-header\"", "id=\"site-sidebar\"", "href=\"/posts\"", "</html>")
                    .doesNotContain("Whitelabel");
        }
    }

    /** CSRF 토큰 없이 보낸 폼처럼 거부된 요청은 403 상태로 권한 안내 화면을 그린다. */
    @Test
    void rendersForbiddenPageInSiteLayout() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/about"))
                .header("Accept", "text/html").POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("<title>볼 수 없는 페이지입니다 — dean.p</title>", "class=\"site-header\"");
    }

    /** HTML을 원하지 않는 요청에는 지금처럼 JSON 오류 본문을 준다. */
    @Test
    void keepsJsonErrorForNonBrowserRequests() throws Exception {
        HttpResponse<String> response = get("/no-such-page", "application/json");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"status\":404").doesNotContain("site-header");
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept).build(), HttpResponse.BodyHandlers.ofString());
    }
}
