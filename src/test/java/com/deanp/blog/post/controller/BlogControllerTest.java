package com.deanp.blog.post.controller;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.PostSeriesOption;
import com.deanp.blog.post.service.PostService;
import com.deanp.blog.visitor.controller.VisitorCookieIdentifier;
import com.deanp.blog.visitor.service.VisitorPostViewTrackingService;
import com.deanp.blog.visitor.service.VisitorRequestMetadata;
import com.deanp.blog.visitor.service.VisitorRequestMetadataFactory;
import com.deanp.blog.visitor.service.TrustedProxyMatcher;
import com.deanp.blog.visitor.service.VisitorTrackingRequestPolicy;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.IntStream;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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

    /** 빈 검색은 전체 조회 없이 안내 화면을 표시한다. */
    @Test
    void blankSearchShowsGuidance() throws Exception {
        mockMvc.perform(get("/search").param("q", "  "))
                .andExpect(status().isOk()).andExpect(view().name("search"))
                .andExpect(content().string(containsString("제목·요약·본문에서 검색합니다.")))
                .andExpect(content().string(not(containsString("class=\"results-count\""))));
        verify(postService, never()).findAll(any(), any(), any(), any());
    }

    /** 검색 결과의 제목과 편수 및 상세 링크를 렌더링한다. */
    @Test
    void dedicatedSearchRendersResults() throws Exception {
        var post = postView();
        when(postService.findAll(null, null, null, "Java")).thenReturn(List.of(post));
        mockMvc.perform(get("/search").param("q", "Java"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(post.title())))
                .andExpect(content().string(containsString("1편")))
                .andExpect(content().string(containsString("/posts/" + post.slug())));
    }

    /** 검색 페이지에는 공통 메뉴만 표시하고 글 분류는 표시하지 않는다. */
    @Test
    void searchSidebarOmitsPostFilters() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"sidebar-filters\""))))
                .andExpect(content().string(containsString("id=\"site-sidebar\"")))
                .andExpect(content().string(containsString("aria-controls=\"site-sidebar\"")));
    }

    /** 인증 미구현 상태에서 사용자 메뉴는 비활성 로그인 안내만 제공한다. */
    @Test
    void accountMenuShowsHonestLoginPlaceholder() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-controls=\"account-panel\"")))
                .andExpect(content().string(containsString("로그인 기능을 준비하고 있습니다.")))
                .andExpect(content().string(containsString("type=\"button\" disabled")))
                .andExpect(content().string(not(containsString("href=\"/login\""))));
    }

    /** 검색어를 정규화하고 결과가 없을 때 안내한다. */
    @Test
    void dedicatedSearchNormalizesQuery() throws Exception {
        mockMvc.perform(get("/search").param("q", " missing "))
                .andExpect(status().isOk()).andExpect(model().attribute("search", "missing"))
                .andExpect(content().string(containsString("검색어에 맞는 글이 없습니다.")))
                .andExpect(content().string(containsString("0편")));
        verify(postService).findAll(null, null, null, "missing");
    }

    /** 공통 사이드 메뉴에 탐색과 테마를 배치하고 접이식 검색을 제거한다. */
    @Test
    void sidebarNavigationAndThemeAreRendered() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/search\"")))
                .andExpect(content().string(containsString("class=\"sidebar-theme\"")))
                .andExpect(content().string(containsString("id=\"site-sidebar\"")))
                .andExpect(content().string(containsString("class=\"sidebar-filters\"")))

                .andExpect(content().string(not(containsString("search-disclosure"))));
    }

    /** 목록 템플릿이 필터 목록을 반복할 수 있도록 기본 빈 선택지를 제공한다. */
    @BeforeEach
    void defaultFilterOptions() {
        when(postService.findAll(any(), any(), any(), any())).thenReturn(List.of());
        when(postService.findCategories()).thenReturn(List.of());
        when(postService.findSeries(null)).thenReturn(List.of());
        when(postService.findSeries(null, null)).thenReturn(Optional.empty());
        when(postService.findTags()).thenReturn(List.of());
    }

    /** 실제 템플릿으로 시안의 목록·선택 표시·편수·원본 연재 번호를 검증한다. */
    @Test
    void rendersArchiveDesignAndWritesBrowserFixtures() throws Exception {
        var selected = new PostSeriesOption("spring-blog", "Spring으로 블로그 만들기",
                "프로젝트 준비부터 배포까지, 작은 블로그를 완성하는 과정입니다.", "dev", 3, "ACTIVE");
        when(postService.countPublishedPosts()).thenReturn(12L);
        when(postService.findCategories()).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("dev", "개발", 8),
                new com.deanp.blog.post.PostFilterOption("project", "프로젝트", 2),
                new com.deanp.blog.post.PostFilterOption("life", "일상", 2)));
        when(postService.findTags()).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("java", "Java"),
                new com.deanp.blog.post.PostFilterOption("spring", "Spring"),
                new com.deanp.blog.post.PostFilterOption("postgres", "PostgreSQL")));
        var series = List.of(selected, new PostSeriesOption("database", "처음 배우는 데이터베이스",
                "데이터베이스를 차근차근 배웁니다.", "dev", 4, "COMPLETED"));
        when(postService.findSeries("dev")).thenReturn(series);
        when(postService.findSeries("dev", "spring-blog")).thenReturn(Optional.of(selected));
        var titles = List.of("Spring Boot로 나만의 블로그 시작하기", "카테고리와 태그, 단순하지만 유연하게", "글 목록과 상세 화면 만들기");
        var summaries = List.of("작게 시작해서 오래 운영할 수 있는 블로그. 첫 번째로 프로젝트의 구조와 기본 설정을 정리했습니다.",
                "글을 쉽게 찾을 수 있도록 분류를 설계합니다. 복잡한 계층 대신 단순한 관계부터 시작합니다.",
                "설계한 데이터를 화면으로 연결합니다. 글 목록과 상세 페이지를 차근차근 만들어 봅니다.");
        var posts = IntStream.range(0, 3).mapToObj(i -> new PostView(new UUID(0, i + 100),
                "preview-" + i, titles.get(i), summaries.get(i), LocalDate.of(2026, 9, 18).minusDays(i),
                6, List.of("Java", i == 0 ? "Spring" : "PostgreSQL"), "", "/media/should-not-render.png",
                "개발", "spring-blog", selected.name(), i * 2 + 1)).toList();
        when(postService.findAll("dev", "spring-blog", null, null)).thenReturn(posts);
        var result = mockMvc.perform(get("/posts").param("category", "dev").param("series", "spring-blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 3편 · 연재 중")))
                .andExpect(content().string(containsString("4편 · 완결")))
                .andExpect(content().string(containsString("03. ")))
                .andExpect(content().string(containsString("05. ")))
                .andExpect(content().string(not(containsString("02. "))))
                .andExpect(content().string(containsString("aria-current=\"true\"")))
                .andExpect(content().string(containsString("개발 ×")))
                .andExpect(content().string(containsString("Spring으로 블로그 만들기 · 3편 →")))
                .andExpect(content().string(not(containsString("should-not-render.png"))))
                .andReturn();
        Path output = Path.of("build/reports/ui-preview/selected.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        var empty = mockMvc.perform(get("/posts").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("선택한 조건에 맞는 글이 없습니다.")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andReturn();
        Files.writeString(output.resolveSibling("empty.html"), empty.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

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
        when(postService.findAll(null, null, null, null)).thenReturn(List.of(post));
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

    /**
     * 글 수별 홈 모델, 카드 순서, 전체 목록 링크를 검증하고 브라우저 확인용 HTML을 저장한다.
     *
     * @throws Exception MVC 요청 또는 검증용 HTML 저장에 실패한 경우
     */
    @Test
    void rendersHomePreviewForZeroOneAndManyPosts() throws Exception {
        for (int count : List.of(0, 1, 10)) {
            List<PostView> posts = IntStream.rangeClosed(1, count)
                    .mapToObj(index -> new PostView(
                            new UUID(0, index), "layout-post-" + index, "레이아웃 검증 글 " + index,
                            "PC에서는 두 열로, 모바일에서는 기존 구성으로 표시되는지 확인합니다.",
                            LocalDate.of(2026, 9, 18).minusDays(index), index,
                            List.of("레이아웃"), "<p>검증 본문</p>"))
                    .toList();
            when(postService.findAll()).thenReturn(posts);

            var result = mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(model()
                            .attribute("featuredPosts", posts.stream().limit(6).toList()))
                    .andExpect(model()
                            .attribute("recentPosts", posts.stream().skip(1).toList()))
                    .andExpect(content().string(containsString("href=\"/posts\" aria-label=\"최근 생각 더 보기\"")))
                    .andExpect(content().string(containsString("href=\"/posts\" aria-label=\"새로운 글 더 보기\"")))
                    .andReturn();
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertEquals(Math.min(count, 6),
                    java.util.regex.Pattern.compile("class=\"feature-card\"").matcher(html).results().count());
            org.junit.jupiter.api.Assertions.assertEquals(Math.max(0, count - 1),
                    java.util.regex.Pattern.compile("class=\"post-card\"").matcher(html).results().count());

            // 실제 Thymeleaf 렌더링 결과를 브라우저의 반응형 배치 검증에 사용한다.
            Path output = Path.of("build/reports/home-layout", count + ".html");
            Files.createDirectories(output.getParent());
            Files.writeString(output, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
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
        when(postService.findAll(null, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("아직 공개된 글이 없습니다.")));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("아직 공개된 글이 없습니다.")));
    }

    /**
     * 카테고리와 태그 선택 상태, GET 폼, 빈 결과 및 URL 값 정규화를 검증한다.
     * @throws Exception 화면 요청 실패
     */
    @Test
    void rendersCombinedFiltersAndEmptyResults() throws Exception {
        when(postService.findCategories()).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("dev", "개발"),
                new com.deanp.blog.post.PostFilterOption("life", "일상")));
        when(postService.findTags()).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("java", "Java"),
                new com.deanp.blog.post.PostFilterOption("spring", "Spring")));
        when(postService.findSeries("dev")).thenReturn(List.of(
                new PostSeriesOption("boot-camp", "부트 캠프", "소개", "dev")));
        when(postService.findAll("dev", "boot-camp", "java", "검색어")).thenReturn(List.of(postView()));
        when(postService.findSeries("dev", "boot-camp")).thenReturn(Optional.of(
                new PostSeriesOption("boot-camp", "부트 캠프", "소개", "dev")));
        var result = mockMvc.perform(get("/posts")
                        .param("category", " dev ")
                        .param("series", " boot-camp ")
                        .param("tag", " java ")
                        .param("q", " 검색어 "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategory", "dev"))
                .andExpect(model().attribute("selectedSeries", "boot-camp"))
                .andExpect(model().attribute("selectedTag", "java"))
                .andExpect(model().attribute("search", "검색어"))
                .andExpect(content().string(containsString("부트 캠프")))
                .andExpect(content().string(containsString("tag=java")))
                .andExpect(content().string(not(containsString("search-disclosure"))))
                .andReturn();
        Path output = Path.of("build/reports/series/selected.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        verify(postService).findAll("dev", "boot-camp", "java", "검색어");

        mockMvc.perform(get("/posts").param("tag", "missing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("선택한 조건에 맞는 글이 없습니다.")))
                .andExpect(model().attribute("selectedTag", "missing"));
        mockMvc.perform(get("/posts").param("category", " ").param("tag", ""))
                .andExpect(status().isOk()).andExpect(model().attribute("hasFilters", false));
        verify(postService).findAll(null, null, null, null);
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
        when(postService.findAll("spring", null, null, null)).thenReturn(List.of(post));
        when(postService.findSeries("spring")).thenReturn(List.of());

        mockMvc.perform(get("/posts").param("category", "spring"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("Spring 카테고리 글")));

        verify(postService).findAll("spring", null, null, null);
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
