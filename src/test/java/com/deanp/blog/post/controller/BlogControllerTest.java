package com.deanp.blog.post.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.media.service.PostFileService;
import com.deanp.blog.media.service.PostFileService.PostFile;
import com.deanp.blog.post.PostView;
import com.deanp.blog.post.PostSeriesOption;
import com.deanp.blog.post.service.BoardService;
import com.deanp.blog.post.service.BoardService.BoardView;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BlogController.class)
@Import({VisitorCookieIdentifier.class, VisitorTrackingRequestPolicy.class,
        TrustedProxyMatcher.class, VisitorRequestMetadataFactory.class, SecurityConfiguration.class,
        SidebarFilterModel.class})
class BlogControllerTest {

    private static final BoardView DEV_BOARD = new BoardView(new UUID(0, 0xb1), "dev", "개발", "개발하며 배운 것들, 만들어 가는 이야기.");
    private static final BoardView NOTES_BOARD = new BoardView(new UUID(0, 0xb2), "notes", "노트", "개발 밖의 정보와 생각을 나눕니다.");

    /** 헤더와 메뉴가 공통 높이 변수를 사용하고 모바일 높이는 유지되는지 확인한다. */
    @Test
    void sidebarHeightMatchesDesktopHeader() throws Exception {
        mockMvc.perform(get("/css/site.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--site-header-height: 74px;")))
                .andExpect(content().string(containsString("height: var(--site-header-height);")))
                .andExpect(content().string(containsString(".site-sidebar { top: var(--site-header-height); height: calc(100dvh - var(--site-header-height));")))
                .andExpect(content().string(not(containsString(".site-sidebar { top: 88px;"))))
                .andExpect(content().string(containsString(":root { --site-header-height: 72px; }")))
                .andExpect(content().string(containsString(".sidebar-backdrop { top: var(--site-header-height); }")));
    }

    /** 글 상세에 사용하는 웹폰트 파일과 글꼴 선언을 제공하는지 확인한다. */
    @Test
    void servesArticleFont() throws Exception {
        mockMvc.perform(get("/fonts/pretendard/PretendardVariable.woff2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/css/site.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("font-family: \"Pretendard Article\"")))
                .andExpect(content().string(containsString(".post-detail-page .article-header")))
                .andExpect(content().string(containsString("margin: 0 auto 65px; text-align: left;")));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private PostFileService postFiles;

    @MockitoBean
    private VisitorPostViewTrackingService visitorPostViewTrackingService;

    /** 글 상세는 쿼리 없는 대표 주소, 글 요약 설명, 대표 이미지 공유 정보를 head에 넣는다. */
    @Test
    void postDetailHeadHasCanonicalDescriptionAndShareImage() throws Exception {
        PostView post = new PostView(new UUID(0, 77), "seo-post", "검색 글", "검색 결과에 보일 요약",
                LocalDate.of(2026, 10, 1), 1, List.of(), "<p>본문</p>", "/media/posts/a/cover.png");
        when(postService.findBySlug("seo-post")).thenReturn(Optional.of(post));

        mockMvc.perform(get("/posts/seo-post").param("category", "dev"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"http://localhost/posts/seo-post\">")))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"검색 결과에 보일 요약\">")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"article\">")))
                .andExpect(content().string(containsString("<meta property=\"og:image\" content=\"http://localhost/media/posts/a/cover.png\">")))
                .andExpect(content().string(containsString("<meta name=\"twitter:card\" content=\"summary_large_image\">")))
                .andExpect(content().string(not(containsString("name=\"robots\""))));
    }

    /** 조건 없는 글 목록은 색인하고, 필터·페이지 조건이 붙은 목록과 검색 화면은 색인에서 뺀다. */
    @Test
    void filteredListsAndSearchAreNotIndexed() throws Exception {
        mockMvc.perform(get("/dev"))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"http://localhost/dev\">")))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"개발 공부를 하며 배운 것과 나만의 이야기를 기록하는 dean.p의 개인 공간입니다.\">")))
                .andExpect(content().string(not(containsString("name=\"robots\""))));
        mockMvc.perform(get("/dev").param("category", "dev"))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, follow\">")))
                .andExpect(content().string(not(containsString("rel=\"canonical\""))));
        mockMvc.perform(get("/search").param("q", "spring"))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, follow\">")));
    }

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

    /** 목록에서 고른 분류가 글 링크로 전달되어 상세 사이드바에 유지되고, 상세의 태그는 날짜 아래에 표시된다. */
    @Test
    void postDetailKeepsSidebarFiltersAndShowsTagsBelowDate() throws Exception {
        when(postService.findCategories("dev")).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("dev", "개발", 2),
                new com.deanp.blog.post.PostFilterOption("life", "일상", 1)));
        when(postService.findSeries("dev", "dev")).thenReturn(List.of());
        when(postService.findPage("dev", "dev", null, List.of(), null, 1, 20))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(0, 20), 1));
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/dev").param("category", "dev"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/posts/real-post?category=dev\"")));
        mockMvc.perform(get("/dev"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postLinkQuery", ""));

        String detail = mockMvc.perform(get("/posts/real-post").param("category", "dev"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategory", "dev"))
                .andExpect(content().string(containsString("class=\"sidebar-filters\"")))
                .andExpect(content().string(containsString("href=\"/dev?category=life&amp;tag=&amp;q=&amp;size=20\"")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String activeCategory = detail.substring(detail.indexOf("aria-current=\"true\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                activeCategory.indexOf("개발") < activeCategory.indexOf("</a>"), "선택한 카테고리가 활성 상태여야 한다");
        org.junit.jupiter.api.Assertions.assertTrue(
                detail.indexOf("class=\"post-meta\"") < detail.indexOf("class=\"tag-list\""), "태그는 날짜 아래에 있어야 한다");
        Path output = Path.of("build/reports/ui-preview/post-detail.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, detail);
    }

    /** 예전 글 목록 주소는 필터 쿼리를 유지한 채 기본 게시판 목록으로 영구 이동한다. */
    @Test
    void legacyPostListRedirectsToDefaultBoard() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/dev"));
        mockMvc.perform(get("/posts?category=algorithm&size=50"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/dev?category=algorithm&size=50"));
    }

    /** 노트 게시판 목록은 노트 글만 조회하고, 제목·소개·필터 링크·사이드 메뉴를 노트 기준으로 그린다. 없는 게시판은 404다. */
    @Test
    void rendersNotesBoardAndRejectsUnknownBoard() throws Exception {
        when(postService.findPage("notes", null, null, List.of(), null, 1, 20))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>노트</h1>")))
                .andExpect(content().string(containsString("개발 밖의 정보와 생각을 나눕니다.")))
                .andExpect(content().string(containsString("<a id=\"sidebar-posts-title\" href=\"/notes\">노트</a>")))
                .andExpect(content().string(containsString("<a href=\"/dev\">개발</a>")))
                .andExpect(content().string(containsString("href=\"/notes?tag=&amp;q=&amp;size=20\"")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"http://localhost/notes\">")));
        verify(postService).findPage("notes", null, null, List.of(), null, 1, 20);
        verify(postService).findCategories("notes");

        mockMvc.perform(get("/no-such-board")).andExpect(status().isNotFound());
    }

    /** 게시판 주소는 점이 없는 한 단어만 받으므로 사이트 최상위의 정적 파일은 그대로 열린다. */
    @Test
    void boardPathDoesNotShadowRootStaticFiles() throws Exception {
        mockMvc.perform(get("/google7427df080ec63bba.html"))
                .andExpect(status().isOk())
                .andExpect(content().string("google-site-verification: google7427df080ec63bba.html"));
    }

    /** 노트 글 상세는 사이드 메뉴 필터와 목록으로 돌아가는 링크를 그 글의 게시판 기준으로 그린다. */
    @Test
    void postDetailUsesItsBoard() throws Exception {
        PostView note = new PostView(new UUID(0, 9), "note-post", "노트 글", "요약", LocalDate.of(2026, 10, 1), 1,
                List.of(), "<p>본문</p>", null, null, null, null, null, "notes", "노트");
        when(postService.findBySlug("note-post")).thenReturn(Optional.of(note));

        mockMvc.perform(get("/posts/note-post"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<a class=\"back-link\" href=\"/notes\">← 노트</a>")))
                .andExpect(content().string(containsString("<a id=\"sidebar-posts-title\" href=\"/notes\">노트</a>")));
        verify(postService).findCategories("notes");
    }

    /** 검색 페이지에서도 기본 게시판(개발) 아래에 분류 필터를 두되, 게시판 화면이 아니므로 접어 둔다. */
    @Test
    void searchSidebarKeepsPostFiltersCollapsed() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"sidebar-filters\"")))
                .andExpect(content().string(containsString("aria-expanded=\"false\" aria-label=\"개발 메뉴 펼치기\"")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"sidebar-posts-menu\"[^>]*hidden.*")))
                .andExpect(content().string(containsString("id=\"site-sidebar\"")))
                .andExpect(content().string(containsString("aria-controls=\"site-sidebar\"")));
    }

    /** 로그인하지 않은 사용자의 메뉴는 로그인 링크만 제공하고 관리·로그아웃은 숨긴다. */
    @Test
    void accountMenuShowsLoginLinkToAnonymousUser() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-controls=\"account-panel\"")))
                .andExpect(content().string(containsString("글을 쓰려면 로그인하세요.")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("href=\"/admin\""))))
                .andExpect(content().string(not(containsString("sidebar-admin"))))
                .andExpect(content().string(not(containsString("action=\"/logout\""))));
    }

    /** 글 관리 권한이 있는 사용자의 메뉴는 관리 대시보드 링크와 로그아웃을 제공한다. */
    @Test
    void accountMenuShowsAdminLinksToWriter() throws Exception {
        mockMvc.perform(get("/search").with(user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin 님으로 로그인했습니다.")))
                .andExpect(content().string(containsString("href=\"/admin\"")))
                .andExpect(content().string(containsString("<a id=\"sidebar-admin-title\" href=\"/admin\">관리자</a>")))
                .andExpect(content().string(containsString("href=\"/admin/categories\"")))
                .andExpect(content().string(not(containsString("aria-current=\"page\""))))
                .andExpect(content().string(containsString("aria-expanded=\"false\" aria-label=\"관리자 메뉴 펼치기\"")))
                .andExpect(content().string(containsString("role=\"group\" aria-labelledby=\"sidebar-admin-title\" hidden")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(not(containsString("href=\"/login\""))));
    }

    /** 검색 링크는 사용자 메뉴 앞에 있으며 메뉴를 열지 않아도 접근할 수 있다. */
    @Test
    void headerSearchPrecedesAccountMenu() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*<a class=\"header-search\" href=\"/search\" aria-label=\"검색\">.*<div class=\"account-menu\">.*")));
    }

    /** 첫 화면의 메뉴는 닫혀 있고 헤더 버튼에서 열 수 있다. */
    @Test
    void sidebarStartsClosed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-controls=\"site-sidebar\" aria-expanded=\"false\"")))
                .andExpect(content().string(containsString("id=\"site-sidebar\" class=\"site-sidebar\" aria-label=\"사이트 메뉴\" hidden")));
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

    /** 공통 메뉴에는 소개와 게시판(개발·노트)을 순서대로 표시하고, 게시판 목록에서는 그 게시판의 하위 메뉴(분류 필터)를 펼치며 테마 버튼은 유지한다. */
    @Test
    void sidebarNavigationAndThemeAreRendered() throws Exception {
        mockMvc.perform(get("/dev"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*class=\"sidebar-nav\"[^>]*>\\s*<a class=\"sidebar-leaf\" href=\"/about\">소개</a>\\s*<div class=\"sidebar-group\">.*<a id=\"sidebar-posts-title\" href=\"/dev\">개발</a>.*<a href=\"/notes\">노트</a>.*</nav>.*")))
                .andExpect(content().string(containsString("aria-expanded=\"true\" aria-label=\"개발 메뉴 접기\"")))
                .andExpect(content().string(not(matchesPattern("(?s).*id=\"sidebar-posts-menu\"[^>]*hidden.*"))))
                .andExpect(content().string(not(containsString(">검색</a>"))))
                .andExpect(content().string(not(containsString("<strong>메뉴</strong>"))))
                .andExpect(content().string(not(containsString("<span>테마</span>"))))
                .andExpect(content().string(not(containsString("class=\"sidebar-heading\""))))
                .andExpect(content().string(not(containsString("class=\"sidebar-close\""))))
                .andExpect(content().string(containsString("class=\"sidebar-theme\"")))
                .andExpect(content().string(containsString("data-theme-toggle")))
                .andExpect(content().string(containsString("id=\"site-sidebar\"")))
                .andExpect(content().string(containsString("class=\"sidebar-filters\"")))

                .andExpect(content().string(not(containsString("search-disclosure"))));
    }

    /** 처음에는 태그 20개만 렌더링하고 추가 요청의 구간·남은 태그 여부를 응답한다. */
    @Test
    void tagFiltersLoadInBatchesOfTwenty() throws Exception {
        var first = IntStream.range(0, 21)
                .mapToObj(index -> new com.deanp.blog.post.PostFilterOption("tag-" + index, "태그" + index))
                .toList();
        when(postService.findTags("dev", 0, 21)).thenReturn(first);
        mockMvc.perform(get("/dev"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-offset=\"20\"")))
                .andExpect(content().string(containsString("태그19</a>")))
                .andExpect(content().string(not(containsString("태그20</a>"))));

        when(postService.findTags("dev", 20, 21)).thenReturn(List.of(first.get(20)));
        mockMvc.perform(get("/posts/tags/more").param("board", "dev").param("offset", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].slug").value("tag-20"))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/posts/tags/more").param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    /** 복수 태그 선택을 정규화하고 각 필터를 별도로 해제할 수 있도록 렌더링한다. */
    @Test
    void rendersMultipleTagFiltersAndKeepsCategorySelection() throws Exception {
        var options = List.of(new com.deanp.blog.post.PostFilterOption("java", "Java"),
                new com.deanp.blog.post.PostFilterOption("spring", "Spring"));
        when(postService.findCategories("dev")).thenReturn(List.of(new com.deanp.blog.post.PostFilterOption("dev", "개발", 2)));
        when(postService.findTags("dev")).thenReturn(options);
        when(postService.findTags("dev", 0, 21)).thenReturn(options);
        when(postService.findPage("dev", "dev", null, List.of("java", "spring"), null, 1, 20))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/dev").param("category", "dev").param("tag", " java , spring, java "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedTag", "java,spring"))
                .andExpect(content().string(containsString("Java ×")))
                .andExpect(content().string(containsString("Spring ×")))
                .andExpect(content().string(not(containsString("aria-hidden=\"true\"> ✓</span>"))));
        verify(postService).findPage("dev", "dev", null, List.of("java", "spring"), null, 1, 20);
    }

    /** 첫 태그 구간 밖에서 선택한 태그도 사이드바 상단에 활성 상태로 유지한다. */
    @Test
    void keepsSelectedTagVisibleOutsideInitialBatch() throws Exception {
        var first = new com.deanp.blog.post.PostFilterOption("java", "Java");
        var selected = new com.deanp.blog.post.PostFilterOption("kotlin", "Kotlin");
        when(postService.findTags("dev", 0, 21)).thenReturn(List.of(first));
        when(postService.findTags("dev")).thenReturn(List.of(first, selected));

        mockMvc.perform(get("/dev").param("tag", "kotlin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tag-slug=\"kotlin\"")))
                .andExpect(content().string(containsString("Kotlin ×")));
    }

    /** 기본 20개·선택한 페이지 크기·페이지 이동과 필터 유지 및 범위 오류를 검증한다. */
    @Test
    void rendersPostPaginationAndPageSizeChoices() throws Exception {
        when(postService.findPage("dev", null, null, List.of(), null, 1, 20))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(0, 20), 41));
        when(postService.findPage("dev", null, null, List.of(), null, 2, 20))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(1, 20), 41));
        when(postService.findPage("dev", null, null, List.of(), null, 1, 10))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(0, 10), 41));
        when(postService.findPage("dev", null, null, List.of(), null, 4, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 20), 41));

        mockMvc.perform(get("/dev"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 20))
                .andExpect(model().attribute("totalPages", 3))
                .andExpect(content().string(containsString("41편")))
                .andExpect(content().string(containsString("글 목록 페이지")));
        mockMvc.perform(get("/dev").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
        mockMvc.perform(get("/dev").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 10))
                .andExpect(content().string(containsString("size=10")))
                .andExpect(content().string(containsString("name=\"size\" onchange=\"this.form.submit()\"")))
                .andExpect(content().string(not(containsString("<button type=\"submit\">적용</button>"))));
        mockMvc.perform(get("/dev").param("page", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/dev").param("size", "30")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/dev").param("page", "4")).andExpect(status().isNotFound());
    }

    /** 목록 템플릿이 필터 목록을 반복할 수 있도록 기본 빈 선택지를 제공한다. */
    @BeforeEach
    void defaultFilterOptions() {
        when(boardService.findActive()).thenReturn(List.of(DEV_BOARD, NOTES_BOARD));
        when(boardService.findActive("dev")).thenReturn(Optional.of(DEV_BOARD));
        when(boardService.findActive("notes")).thenReturn(Optional.of(NOTES_BOARD));
        when(postService.findAll(any(), any(), any(), any())).thenReturn(List.of());
        when(postService.findPage(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(postService.findCategories("dev")).thenReturn(List.of());
        when(postService.findSeries("dev", null)).thenReturn(List.of());
        when(postService.findSeries("dev", null, null)).thenReturn(Optional.empty());
        when(postService.findTags("dev")).thenReturn(List.of());
    }

    /** 실제 템플릿으로 시안의 목록·선택 표시·편수·원본 연재 번호를 검증한다. */
    @Test
    void rendersArchiveDesignAndWritesBrowserFixtures() throws Exception {
        var selected = new PostSeriesOption("spring-blog", "Spring으로 블로그 만들기",
                "프로젝트 준비부터 배포까지, 작은 블로그를 완성하는 과정입니다.", "dev", 3, "ACTIVE");
        when(postService.countPublishedPosts("dev")).thenReturn(12L);
        when(postService.findCategories("dev")).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("dev", "개발", 8),
                new com.deanp.blog.post.PostFilterOption("project", "프로젝트", 2),
                new com.deanp.blog.post.PostFilterOption("life", "일상", 2)));
        when(postService.findTags("dev")).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("java", "Java"),
                new com.deanp.blog.post.PostFilterOption("spring", "Spring"),
                new com.deanp.blog.post.PostFilterOption("postgres", "PostgreSQL")));
        var series = List.of(selected, new PostSeriesOption("database", "처음 배우는 데이터베이스",
                "데이터베이스를 차근차근 배웁니다.", "dev", 4, "COMPLETED"));
        when(postService.findSeries("dev", "dev")).thenReturn(series);
        when(postService.findSeries("dev", "dev", "spring-blog")).thenReturn(Optional.of(selected));
        var titles = List.of("Spring Boot로 나만의 블로그 시작하기", "카테고리와 태그, 단순하지만 유연하게", "글 목록과 상세 화면 만들기");
        var summaries = List.of("작게 시작해서 오래 운영할 수 있는 블로그. 첫 번째로 프로젝트의 구조와 기본 설정을 정리했습니다.",
                "글을 쉽게 찾을 수 있도록 분류를 설계합니다. 복잡한 계층 대신 단순한 관계부터 시작합니다.",
                "설계한 데이터를 화면으로 연결합니다. 글 목록과 상세 페이지를 차근차근 만들어 봅니다.");
        var posts = IntStream.range(0, 3).mapToObj(i -> new PostView(new UUID(0, i + 100),
                "preview-" + i, titles.get(i), summaries.get(i), LocalDate.of(2026, 9, 18).minusDays(i),
                6, List.of("Java", i == 0 ? "Spring" : "PostgreSQL"), "", i == 0 ? "/media/list-thumbnail.png" : null,
                "개발", "spring-blog", selected.name(), i * 2 + 1)).toList();
        when(postService.findPage("dev", "dev", "spring-blog", List.of(), null, 1, 20))
                .thenReturn(new PageImpl<>(posts, PageRequest.of(0, 20), 3));
        var result = mockMvc.perform(get("/dev").param("category", "dev").param("series", "spring-blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 3편 · 연재 중")))
                .andExpect(content().string(containsString("4편 · 완결")))
                .andExpect(content().string(containsString("03. ")))
                .andExpect(content().string(containsString("05. ")))
                .andExpect(content().string(not(containsString("02. "))))
                .andExpect(content().string(containsString("aria-current=\"true\"")))
                .andExpect(content().string(containsString("개발 ×")))
                .andExpect(content().string(containsString("Spring으로 블로그 만들기 · 3편 →")))
                .andExpect(content().string(containsString("class=\"post-list-thumbnail\" src=\"/media/list-thumbnail.png\"")))
                .andReturn();
        Path output = Path.of("build/reports/ui-preview/selected.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        var empty = mockMvc.perform(get("/dev").param("q", "<script>alert(1)</script>"))
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
        when(postService.findPage("dev", null, null, List.of(), null, 1, 20))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1));
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(post));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("실제 발행 글")));

        mockMvc.perform(get("/dev"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("PostgreSQL에서 읽은 글입니다.")));

        mockMvc.perform(get("/posts/real-post"))
                .andExpect(status().isOk())
                .andExpect(view().name("post"))
                .andExpect(content().string(not(containsString("class=\"article-summary\""))))
                .andExpect(content().string(containsString("<strong>Markdown</strong> 본문")));

        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(view().name("about"));
    }

    /** 첨부 파일이 있으면 글 끝에 이름·크기와 내려받기 링크 목록을 붙이고, 없으면 목록을 그리지 않는다. */
    @Test
    void rendersAttachmentListBelowArticle() throws Exception {
        UUID postId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID fileId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PostView post = new PostView(postId, "real-post", "실제 발행 글", "요약", LocalDate.of(2026, 8, 31), 1,
                List.of(), "<p>본문</p>\n");
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(post));

        mockMvc.perform(get("/posts/real-post"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("article-attachments"))));

        when(postFiles.findByPost(postId)).thenReturn(List.of(new PostFile(fileId, "slides.pdf", 1_572_864)));
        mockMvc.perform(get("/posts/real-post"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"article-attachments\"")))
                .andExpect(content().string(containsString("href=\"/media/files/" + fileId + "\"")))
                .andExpect(content().string(containsString("slides.pdf")))
                .andExpect(content().string(containsString("1.5 MB")));
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
                    .andExpect(content().string(containsString("<h1>개발하고,<br class=\"hero-mobile-break\"> 정보를 나누고,<br><em>생각</em>을 정리하는&nbsp;곳.</h1>")))
                    .andExpect(content().string(containsString(
                            "<meta name=\"description\" content=\"개발 공부를 하며 배운 것과 나만의 이야기를 기록하는 dean.p의 개인 공간입니다.\">")))
                    .andExpect(model()
                            .attribute("featuredPosts", posts.stream().limit(6).toList()))
                    .andExpect(model()
                            .attribute("recentPosts", posts.stream().skip(1).toList()))
                    .andExpect(content().string(containsString("href=\"/dev\" aria-label=\"최근 생각 더 보기\"")))
                    .andExpect(content().string(containsString("href=\"/dev\" aria-label=\"새로운 글 더 보기\"")))
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

        mockMvc.perform(get("/dev"))
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
        when(postService.findCategories("dev")).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("dev", "개발"),
                new com.deanp.blog.post.PostFilterOption("life", "일상")));
        when(postService.findTags("dev")).thenReturn(List.of(
                new com.deanp.blog.post.PostFilterOption("java", "Java"),
                new com.deanp.blog.post.PostFilterOption("spring", "Spring")));
        when(postService.findSeries("dev", "dev")).thenReturn(List.of(
                new PostSeriesOption("boot-camp", "부트 캠프", "소개", "dev")));
        when(postService.findPage("dev", "dev", "boot-camp", List.of("java"), "검색어", 1, 20))
                .thenReturn(new PageImpl<>(List.of(postView()), PageRequest.of(0, 20), 1));
        when(postService.findSeries("dev", "dev", "boot-camp")).thenReturn(Optional.of(
                new PostSeriesOption("boot-camp", "부트 캠프", "소개", "dev")));
        var result = mockMvc.perform(get("/dev")
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
        verify(postService).findPage("dev", "dev", "boot-camp", List.of("java"), "검색어", 1, 20);

        mockMvc.perform(get("/dev").param("tag", "missing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("선택한 조건에 맞는 글이 없습니다.")))
                .andExpect(model().attribute("selectedTag", "missing"));
        mockMvc.perform(get("/dev").param("category", " ").param("tag", ""))
                .andExpect(status().isOk()).andExpect(model().attribute("hasFilters", false));
        verify(postService).findPage("dev", null, null, List.of(), null, 1, 20);
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
        when(postService.findPage("dev", "spring", null, List.of(), null, 1, 20))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1));
        when(postService.findSeries("dev", "spring")).thenReturn(List.of());

        mockMvc.perform(get("/dev").param("category", "spring"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts"))
                .andExpect(content().string(containsString("Spring 카테고리 글")));

        verify(postService).findPage("dev", "spring", null, List.of(), null, 1, 20);
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
                .andExpect(content().string(not(containsString("class=\"article-summary\""))))
                .andExpect(content().string(containsString("<strong>Markdown</strong> 본문")));
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
     * 로그인한 관리자의 상세 조회는 페이지를 렌더링하되 방문 쿠키와 추적 저장을 생략한다.
     *
     * @throws Exception MVC 요청 처리 중 예외가 발생한 경우
     */
    @Test
    void skipsVisitorTrackingForLoggedInAdmin() throws Exception {
        when(postService.findBySlug("real-post")).thenReturn(Optional.of(postView()));

        mockMvc.perform(get("/posts/real-post")
                        .with(user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE))))
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
