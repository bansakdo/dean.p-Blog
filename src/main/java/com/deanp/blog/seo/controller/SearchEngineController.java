package com.deanp.blog.seo.controller;

import com.deanp.blog.post.persistence.query.PublishedPostLink;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 검색엔진이 읽는 robots.txt와 sitemap.xml을 응답한다.
 */
@Controller
@RequiredArgsConstructor
public class SearchEngineController {

    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
    private static final MediaType XML_UTF8 = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);
    private final PostDetailRepository posts;
    private final BoardService boards;

    @Value("${app.seo.indexable:true}")
    private boolean indexable;

    /**
     * 색인을 허용하는 서버는 관리·로그인·검색 화면만 막고 사이트맵 위치를 알린다. 허용하지 않는 서버는 전체를 막는다.
     *
     * @return robots.txt 본문
     */
    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        String body = indexable ? """
                User-agent: *
                Disallow: /admin
                Disallow: /login
                Disallow: /search

                Sitemap: %s/sitemap.xml
                """.formatted(siteUrl()) : """
                User-agent: *
                Disallow: /
                """;
        return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(body);
    }

    /**
     * 홈·게시판 목록·소개 화면과 공개 글 주소를 사이트맵 형식으로 응답한다. 색인을 허용하지 않는 서버는 404로 응답한다.
     *
     * @return sitemap.xml 본문
     */
    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        if (!indexable) {
            return ResponseEntity.notFound().build();
        }
        String siteUrl = siteUrl();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 홈·게시판 목록·소개 화면은 수정 시각 없이 주소만 넣는다.
        appendUrl(xml, siteUrl + "/", null);
        boards.findActive().forEach(board -> appendUrl(xml, siteUrl + board.path(), null));
        appendUrl(xml, siteUrl + "/about", null);

        // 공개 글은 한글 슬러그를 UTF-8로 인코딩하고 마지막 수정일을 함께 넣는다.
        for (PublishedPostLink link : posts.findPublishedPostLinks()) {
            String url = siteUrl + "/posts/" + UriUtils.encodePathSegment(link.slug(), StandardCharsets.UTF_8);
            String lastModified = link.updatedAt() == null ? null
                    : DateTimeFormatter.ISO_LOCAL_DATE.format(link.updatedAt().atOffset(ZoneOffset.UTC));
            appendUrl(xml, url, lastModified);
        }

        return ResponseEntity.ok().contentType(XML_UTF8).body(xml.append("</urlset>\n").toString());
    }

    /**
     * @param xml 작성 중인 사이트맵
     * @param url 절대 주소
     * @param lastModified YYYY-MM-DD 형식 수정일 또는 null
     */
    private void appendUrl(StringBuilder xml, String url, String lastModified) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(url)).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(lastModified).append("</lastmod>");
        }
        xml.append("</url>\n");
    }

    /**
     * 프록시가 넘긴 호스트와 프로토콜을 반영한 사이트 주소다.
     *
     * @return 끝에 / 가 없는 사이트 주소
     */
    private String siteUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
