package com.deanp.blog.post.controller;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.service.PostService;
import com.deanp.blog.visitor.controller.VisitorCookieIdentifier;
import com.deanp.blog.visitor.service.VisitorPostViewTrackingService;
import com.deanp.blog.visitor.service.VisitorRequestMetadata;
import com.deanp.blog.visitor.service.VisitorRequestMetadataFactory;
import com.deanp.blog.visitor.service.TrustedProxyMatcher;
import com.deanp.blog.visitor.service.VisitorTrackingRequestPolicy;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BlogController.class)
@Import({VisitorCookieIdentifier.class, VisitorTrackingRequestPolicy.class,
        TrustedProxyMatcher.class, VisitorRequestMetadataFactory.class})
class BlogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private VisitorPostViewTrackingService visitorPostViewTrackingService;

    @Test
    void rendersPublicPagesFromPostService() throws Exception {
        PostView post = new PostView(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "real-post",
                "실제 발행 글",
                "PostgreSQL에서 읽은 글입니다.",
                LocalDate.of(2026, 8, 31),
                1,
                List.of("Spring Boot"),
                "<p><strong>Markdown</strong> 본문</p>\n"
        );
        when(postService.findAll()).thenReturn(List.of(post));
        when(postService.findAll(null)).thenReturn(List.of(post));
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(post));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("실제 발행 글")));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("PostgreSQL에서 읽은 글입니다.")));

        mockMvc.perform(get("/posts/real-post"))
                .andExpect(status().isOk())
                .andExpect(view().name("post"))
                .andExpect(content().string(containsString("<strong>Markdown</strong> 본문")));

        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(view().name("about"));
    }

    @Test
    void rendersStaticAssetLinksInSharedHead() throws Exception {
        when(postService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/site.css\"")))
                .andExpect(content().string(containsString("src=\"/js/site.js\"")))
                .andExpect(content().string(containsString("src=\"/js/theme.js\"")));
    }

    @Test
    void servesThemeAssets() throws Exception {
        mockMvc.perform(get("/js/theme.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("localStorage")));

        mockMvc.perform(get("/css/site.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#242527")));
    }

    @Test
    void rendersEmptyPublishedPostState() throws Exception {
        when(postService.findAll()).thenReturn(List.of());
        when(postService.findAll(null)).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("아직 공개된 글이 없습니다.")));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("아직 공개된 글이 없습니다.")));
    }

    @Test
    void passesCategoryQueryParameterToPostList() throws Exception {
        PostView post = new PostView(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "spring-category-post",
                "Spring 카테고리 글",
                "카테고리로 걸러진 글입니다.",
                LocalDate.of(2026, 8, 31),
                1,
                List.of(),
                "<p>본문</p>\n"
        );
        when(postService.findAll("spring")).thenReturn(List.of(post));

        mockMvc.perform(get("/posts").param("category", "spring"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("Spring 카테고리 글")));

        verify(postService).findAll("spring");
    }

    /**
     * 상세 글 요청에 방문자 쿠키가 없으면 새 UUID 쿠키를 발급한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void issuesVisitorCookieWhenMissingOnPostDetail() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, matchesPattern(
                        VisitorCookieIdentifier.COOKIE_NAME
                                + "=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}; Path=/; Max-Age=31536000; Expires=.*; HttpOnly; SameSite=Lax"
                )));
    }

    /**
     * 상세 글 요청의 비표준 UUID 쿠키는 새 UUID 쿠키로 교체한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void replacesNonCanonicalVisitorCookieOnPostDetail() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .cookie(new Cookie(VisitorCookieIdentifier.COOKIE_NAME, "1-1-1-1-1")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, matchesPattern(
                        VisitorCookieIdentifier.COOKIE_NAME
                                + "=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}; Path=/; Max-Age=31536000; Expires=.*; HttpOnly; SameSite=Lax"
                )));
    }

    /**
     * 상세 글 요청의 방문자 쿠키가 유효하면 응답 쿠키를 다시 쓰지 않는다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void reusesValidVisitorCookieOnPostDetail() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .cookie(new Cookie(
                                VisitorCookieIdentifier.COOKIE_NAME,
                                "123e4567-e89b-12d3-a456-426614174000"
                        )))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    /**
     * 대문자 UUID 쿠키도 동일한 소문자 canonical 식별자로 추적한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void normalizesUppercaseCanonicalVisitorCookie() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .cookie(new Cookie(
                                VisitorCookieIdentifier.COOKIE_NAME,
                                "123E4567-E89B-12D3-A456-426614174000"
                        )))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(visitorPostViewTrackingService).recordPostView(
                eq("123e4567-e89b-12d3-a456-426614174000"),
                eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                any(VisitorRequestMetadata.class)
        );
    }

    /**
     * 상세 글 요청의 방문자 쿠키가 UUID 형식이 아니면 새 UUID 쿠키로 교체한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void replacesMalformedVisitorCookieOnPostDetail() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .cookie(new Cookie(VisitorCookieIdentifier.COOKIE_NAME, "not-a-uuid")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, matchesPattern(
                        VisitorCookieIdentifier.COOKIE_NAME
                                + "=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}; Path=/; Max-Age=31536000; Expires=.*; HttpOnly; SameSite=Lax"
                )))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("not-a-uuid"))));
    }

    /**
     * 상세 화면 테스트에 사용할 공개 글 뷰를 생성한다.
     *
     * @return 테스트용 공개 글 뷰
     */
    private PostView postView() {
        return new PostView(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "real-post",
                "실제 발행 글",
                "PostgreSQL에서 읽은 글입니다.",
                LocalDate.of(2026, 8, 31),
                1,
                List.of("Spring Boot"),
                "<p><strong>Markdown</strong> 본문</p>\n"
        );
    }


    /**
     * 정상 상세 글 요청은 식별된 방문자와 게시글 식별자로 조회 이벤트 저장을 위임한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void recordsPostViewEventAfterSuccessfulPostDetailLookup() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .cookie(new Cookie(
                                VisitorCookieIdentifier.COOKIE_NAME,
                                "123e4567-e89b-12d3-a456-426614174000"
                        ))
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .header(HttpHeaders.REFERER, "https://search.example.com/result?q=post")
                        .header(HttpHeaders.USER_AGENT, "JUnit Browser")
                        .header("X-Request-Id", "request-123"))
                .andExpect(status().isOk());

        verify(visitorPostViewTrackingService).recordPostView(
                eq("123e4567-e89b-12d3-a456-426614174000"),
                eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                org.mockito.ArgumentMatchers.argThat(metadata ->
                        "203.0.113.0".equals(metadata.ipAddress())
                                && "search.example.com".equals(metadata.referrerHost())
                                && "JUnit Browser".equals(metadata.userAgent())
                                && "request-123".equals(metadata.requestId())
                )
        );
    }


    /**
     * 방문자 저장이 실패해도 게시글 본문 응답은 정상적으로 렌더링한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void keepsPostResponseWhenVisitorTrackingFails() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));
        doThrow(new DataAccessResourceFailureException("tracking database unavailable"))
                .when(visitorPostViewTrackingService)
                .recordPostView(any(String.class), any(UUID.class), any(VisitorRequestMetadata.class));

        mockMvc.perform(get("/posts/real-post")
                        .cookie(new Cookie(
                                VisitorCookieIdentifier.COOKIE_NAME,
                                "123e4567-e89b-12d3-a456-426614174000"
                        )))
                .andExpect(status().isOk())
                .andExpect(view().name("post"))
                .andExpect(content().string(containsString("PostgreSQL에서 읽은 글입니다.")));
    }

    /**
     * 봇 User-Agent의 상세 조회는 페이지를 렌더링하되 방문 쿠키와 추적 저장을 생략한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void skipsVisitorTrackingForBotPostDetailRequest() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; Googlebot/2.1)"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(visitorPostViewTrackingService, never()).recordPostView(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(VisitorRequestMetadata.class)
        );
    }

    /**
     * 헬스체크 URI는 상세 컨트롤러에 도달하지 않아 방문 쿠키와 추적 저장이 없다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void skipsVisitorTrackingForHealthRequests() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(visitorPostViewTrackingService, never()).recordPostView(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(VisitorRequestMetadata.class)
        );
    }

    /**
     * 정적 리소스는 상세 컨트롤러 경로가 아니므로 방문 추적 대상이 아니다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void keepsStaticAssetsUntracked() throws Exception {
        mockMvc.perform(get("/css/site.css"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        mockMvc.perform(get("/js/theme.js"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(visitorPostViewTrackingService, never()).recordPostView(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(VisitorRequestMetadata.class)
        );
    }

    @Test
    void returnsNotFoundForUnknownPost() throws Exception {
        when(postService.findBySlug("not-found")).thenReturn(Optional.empty());

        mockMvc.perform(get("/posts/not-found"))
                .andExpect(status().isNotFound());

        verify(visitorPostViewTrackingService, never()).recordPostView(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(VisitorRequestMetadata.class)
        );
    }
}
