package com.deanp.blog.auth.controller;

import com.deanp.blog.persistence.TestPostgresql;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 폼보다 앞선 HTML이 응답 버퍼를 넘겨 먼저 전송돼도, 세션이 없는 첫 방문에서 폼과 CSRF 토큰이
 * 끝까지 렌더링되는지 실제 서버로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class LoginPageTest {

    private static final TestPostgresql DB = new TestPostgresql();

    @LocalServerPort
    private int port;

    /** @param registry 테스트 전용 데이터베이스와 임시 미디어 저장 위치 설정 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        DB.register(registry);
        Path media = Files.createTempDirectory("blog-login-media");
        registry.add("APP_MEDIA_STORAGE_ROOT", media::toString);
    }

    /** 로컬 일회용 DB를 종료한다. */
    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    /** 쓰는 즉시 응답을 전송하는 상황에서도 세션 쿠키와 CSRF 토큰이 든 로그인 폼을 끝까지 받는다. */
    @Test
    void rendersLoginFormWhenResponseCommitsEarly() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("JSESSIONID="));
        assertThat(response.body()).contains("name=\"_csrf\"", "</form>", "</html>");
    }

    /** 렌더링 중 쓰는 글자마다 바로 전송해, 버퍼를 넘긴 긴 화면처럼 응답을 일찍 확정시키는 필터를 둔다. */
    @TestConfiguration
    static class EarlyCommitConfiguration {

        /** @return 모든 요청의 응답을 즉시 전송 응답으로 감싸는 필터 */
        @Bean
        FilterRegistrationBean<Filter> earlyCommitFilter() {
            Filter filter = (request, response, chain) -> chain.doFilter(request, new EarlyCommitResponse((HttpServletResponse) response));
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    /** 쓸 때마다 flush해 응답을 바로 확정하는 응답 래퍼. */
    static class EarlyCommitResponse extends HttpServletResponseWrapper {

        private PrintWriter writer;

        /** @param response 감쌀 원래 응답 */
        EarlyCommitResponse(HttpServletResponse response) {
            super(response);
        }

        /**
         * @return 쓰기마다 원래 응답을 flush하는 writer
         * @throws IOException 원래 writer를 얻지 못한 경우
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                PrintWriter target = super.getWriter();
                writer = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] buffer, int offset, int length) {
                        target.write(buffer, offset, length);
                        target.flush();
                    }

                    @Override
                    public void flush() {
                        target.flush();
                    }

                    @Override
                    public void close() {
                        target.close();
                    }
                });
            }
            return writer;
        }
    }
}
