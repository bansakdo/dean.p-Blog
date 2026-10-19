package com.deanp.blog.auth.controller;

import com.deanp.blog.persistence.TestPostgresql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내부망 리버스 프록시 뒤에서 X-Forwarded-Proto로 https 리다이렉트 주소를 만들고,
 * X-Forwarded-For의 접속 주소를 방문 기록 IP로 쓰는지 실제 서버로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ForwardedProtoTest {

    private static final TestPostgresql DB = new TestPostgresql();
    private static final UUID AUTHOR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID POST_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** @param registry 테스트 전용 데이터베이스와 임시 미디어 저장 위치 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        DB.register(registry);
        Path media = Files.createTempDirectory("blog-forwarded-media");
        registry.add("APP_MEDIA_STORAGE_ROOT", media::toString);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    /** 프록시가 https라고 알리면 로그인 화면으로 보내는 주소도 https다. 헤더가 없으면 그대로 http다. */
    @Test
    void redirectsToHttpsBehindProxy() throws Exception {
        HttpResponse<Void> proxied = client.send(HttpRequest.newBuilder(uri("/admin"))
                .header("X-Forwarded-Proto", "https").build(), HttpResponse.BodyHandlers.discarding());
        HttpResponse<Void> direct = client.send(HttpRequest.newBuilder(uri("/admin")).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(proxied.statusCode()).isEqualTo(302);
        assertThat(proxied.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).startsWith("https://").endsWith("/login"));
        assertThat(direct.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).startsWith("http://").endsWith("/login"));
    }

    /** 내부망(127.0.0.1)에서 온 요청이면 프록시가 넘긴 X-Forwarded-For 주소를 /24로 익명화해 남긴다. */
    @Test
    void usesForwardedAddressFromInternalProxy() throws Exception {
        jdbc.update("INSERT INTO blog.app_user (id, login_id, password_hash, name) VALUES (?, 'proxy-author', 'hash', '작성자')",
                AUTHOR_ID);
        jdbc.update("""
                INSERT INTO blog.post_detail (id, author_id, slug, title, content, status, published_at)
                VALUES (?, ?, 'proxy-post', '프록시 글', '본문', 'PUBLISHED', now() - interval '1 minute')
                """, POST_ID, AUTHOR_ID);
        try {
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(uri("/posts/proxy-post"))
                    .header("X-Forwarded-For", "203.0.113.9").header("X-Forwarded-Proto", "https").build(),
                    HttpResponse.BodyHandlers.discarding());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForList("SELECT host(ip_address) FROM blog.visitor_event WHERE post_detail_id = ?",
                    String.class, POST_ID)).containsExactly("203.0.113.0");
        } finally {
            jdbc.update("DELETE FROM blog.visitor_daily_summary WHERE post_detail_id = ?", POST_ID);
            jdbc.update("DELETE FROM blog.visitor_event WHERE post_detail_id = ?", POST_ID);
            jdbc.update("DELETE FROM blog.post_detail WHERE id = ?", POST_ID);
            jdbc.update("DELETE FROM blog.visitor WHERE id NOT IN (SELECT visitor_id FROM blog.visitor_event WHERE visitor_id IS NOT NULL)");
            jdbc.update("DELETE FROM blog.app_user WHERE id = ?", AUTHOR_ID);
        }
    }

    /**
     * @param path 서버 경로
     * @return 테스트 서버 주소
     */
    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
