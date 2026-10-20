package com.deanp.blog.seo.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.post.persistence.query.PublishedPostLink;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.deanp.blog.post.service.BoardService;
import com.deanp.blog.post.service.BoardService.BoardView;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 색인을 허용하는 서버의 robots.txt와 sitemap.xml을 검증한다. */
@WebMvcTest(SearchEngineController.class)
@Import(SecurityConfiguration.class)
class SearchEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostDetailRepository posts;

    @MockitoBean
    private BoardService boardService;

    /** Google Search Console 소유권 확인 파일이 사이트 최상위 주소에서 원본 내용 그대로 열린다. 지우면 인증이 풀린다. */
    @Test
    void servesGoogleSiteVerificationFile() throws Exception {
        mockMvc.perform(get("/google7427df080ec63bba.html"))
                .andExpect(status().isOk())
                .andExpect(content().string("google-site-verification: google7427df080ec63bba.html"));
    }

    /** 관리·로그인·검색 화면만 막고 요청 호스트 기준 사이트맵 주소를 알린다. */
    @Test
    void robotsAllowsPublicPagesAndPointsToSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(content().string(containsString("Disallow: /admin\nDisallow: /login\nDisallow: /search\n")))
                .andExpect(content().string(containsString("Sitemap: http://localhost/sitemap.xml")))
                .andExpect(content().string(not(containsString("Disallow: /\n"))));
    }

    /** 고정 화면과 공개 글을 넣고, 한글 슬러그는 인코딩하고 XML 특수문자는 이스케이프한다. */
    @Test
    void sitemapListsFixedPagesAndPublishedPosts() throws Exception {
        when(boardService.findActive()).thenReturn(List.of(
                new BoardView(new UUID(0, 1), "dev", "개발", null), new BoardView(new UUID(0, 2), "notes", "노트", null)));
        when(posts.findPublishedPostLinks()).thenReturn(List.of(
                new PublishedPostLink("한글 글&1", Instant.parse("2026-10-19T16:30:00Z")),
                new PublishedPostLink("no-date", null)));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/xml")))
                .andExpect(content().string(containsString("<url><loc>http://localhost/</loc></url>")))
                .andExpect(content().string(containsString("<url><loc>http://localhost/dev</loc></url>")))
                .andExpect(content().string(containsString("<url><loc>http://localhost/notes</loc></url>")))
                .andExpect(content().string(containsString("<url><loc>http://localhost/about</loc></url>")))
                .andExpect(content().string(containsString(
                        "<url><loc>http://localhost/posts/%ED%95%9C%EA%B8%80%20%EA%B8%80&amp;1</loc><lastmod>2026-10-19</lastmod></url>")))
                .andExpect(content().string(containsString("<url><loc>http://localhost/posts/no-date</loc></url>")));
    }

    /** 프록시가 https 호스트를 알리면 robots.txt의 사이트맵 주소도 그 호스트를 쓴다. */
    @Test
    void usesForwardedHostForSitemapAddress() throws Exception {
        mockMvc.perform(get("/robots.txt").with(request -> {
                    // 운영 Tomcat은 프록시 헤더를 읽어 요청 자체를 https로 바꾸므로 그 결과를 재현한다.
                    request.setScheme("https");
                    request.setServerName("blog.deanp.app");
                    request.setServerPort(443);
                    return request;
                }))
                .andExpect(content().string(containsString("Sitemap: https://blog.deanp.app/sitemap.xml")));
    }
}
