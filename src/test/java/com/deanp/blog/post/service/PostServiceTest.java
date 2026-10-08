package com.deanp.blog.post.service;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.persistence.query.PublicPostRow;
import com.deanp.blog.post.persistence.query.PublicPostQueryRepository.PublishedPage;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostServiceTest {

    private final PostDetailRepository repository = mock(PostDetailRepository.class);
    private final PostService service = new PostService(repository);

    @Test
    void mapsPublishedRowsToViewWithTagsMarkdownDateAndReadingMinutes() {
        UUID postId = UUID.randomUUID();
        when(repository.findPublishedRowsNewestFirst(null)).thenReturn(List.of(
                row(postId, "querydsl-post", "Querydsl Post", "요약", "# 제목\n\n본문 **강조**", Instant.parse("2026-08-30T15:30:00Z"), "Spring"),
                row(postId, "querydsl-post", "Querydsl Post", "요약", "# 제목\n\n본문 **강조**", Instant.parse("2026-08-30T15:30:00Z"), "Querydsl")
        ));

        List<PostView> posts = service.findAll();

        assertThat(posts).hasSize(1);
        PostView post = posts.getFirst();
        assertThat(post.slug()).isEqualTo("querydsl-post");
        assertThat(post.publishedAt()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(post.readingMinutes()).isPositive();
        assertThat(post.tags()).containsExactly("Spring", "Querydsl");
        assertThat(post.htmlContent()).contains("<h1>제목</h1>", "<strong>강조</strong>");
    }

    /** 본문에 글자로 적힌 &nbsp는 공백으로 바꾸되 코드 안의 표기는 그대로 둔다. */
    @Test
    void rendersLiteralNbspAsSpaceOutsideCode() {
        String content = "첫 문단\n\n&nbsp\n\n둘째&nbsp;문단 &NBSP 끝\n\n`&nbsp` 설명\n\n```\na&nbsp;b &nbsp\n```\n";
        when(repository.findPublishedRowsBySlug("nbsp-post")).thenReturn(List.of(
                row(UUID.randomUUID(), "nbsp-post", "Nbsp Post", null, content, Instant.parse("2026-08-31T00:00:00Z"), null)
        ));

        String html = service.findBySlug("nbsp-post").orElseThrow().htmlContent();

        assertThat(html).contains("<p>&nbsp;</p>", "둘째\u00a0문단 &nbsp; 끝");
        assertThat(html).contains("<code>&amp;nbsp</code> 설명", "a&amp;nbsp;b &amp;nbsp\n</code></pre>");
    }

    /** 문단 안 줄바꿈, 인용문, 표, 취소선, URL 자동 링크를 GFM 방식으로 변환하고 HTML 이스케이프는 유지한다. */
    @Test
    void rendersGfmLineBreaksQuotesTablesStrikethroughAndAutolinks() {
        String content = "첫 줄\n둘째 줄\n\n> 인용문\n\n| 이름 | 값 |\n|---|---|\n| a | 1 |\n\n~~취소~~ https://example.com/a <b>굵게</b>\n";
        when(repository.findPublishedRowsBySlug("gfm-post")).thenReturn(List.of(
                row(UUID.randomUUID(), "gfm-post", "Gfm Post", null, content, Instant.parse("2026-08-31T00:00:00Z"), null)
        ));

        String html = service.findBySlug("gfm-post").orElseThrow().htmlContent();

        assertThat(html).contains("첫 줄<br />\n둘째 줄", "<blockquote>\n<p>인용문</p>\n</blockquote>");
        assertThat(html).contains("<table>", "<th>이름</th>", "<td>1</td>", "<del>취소</del>");
        assertThat(html).contains("<a href=\"https://example.com/a\">https://example.com/a</a>", "&lt;b&gt;굵게&lt;/b&gt;");
    }

    @Test
    void escapesInlineHtmlBeforeTemplateRendersTrustedGeneratedMarkdownHtml() {
        UUID postId = UUID.randomUUID();
        when(repository.findPublishedRowsBySlug("safe-post")).thenReturn(List.of(
                row(postId, "safe-post", "Safe Post", null, "본문 <script>alert(1)</script>", Instant.parse("2026-08-31T00:00:00Z"), null)
        ));

        Optional<PostView> post = service.findBySlug("safe-post");

        assertThat(post).isPresent();
        assertThat(post.get().htmlContent()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(post.get().htmlContent()).doesNotContain("<script>");
        assertThat(post.get().tags()).isEmpty();
    }

    @Test
    void passesOptionalCategorySlugToPublishedRowsQuery() {
        UUID postId = UUID.randomUUID();
        when(repository.findPublishedRowsNewestFirst("spring")).thenReturn(List.of(
                row(postId, "spring-post", "Spring Post", "요약", "본문", Instant.parse("2026-08-31T00:00:00Z"), null)
        ));

        List<PostView> posts = service.findAll("spring");

        assertThat(posts).extracting(PostView::slug).containsExactly("spring-post");
        verify(repository).findPublishedRowsNewestFirst("spring");
    }

    @Test
    void passesCombinedBrowseFiltersToPublishedRowsQuery() {
        UUID postId = UUID.randomUUID();
        when(repository.findPublishedRows("spring", "boot", "java", "query")).thenReturn(List.of(
                row(postId, "spring-post", "Spring Post", "요약", "본문", Instant.parse("2026-08-31T00:00:00Z"), null)
        ));

        List<PostView> posts = service.findAll("spring", "boot", "java", "query");

        assertThat(posts).extracting(PostView::slug).containsExactly("spring-post");
        verify(repository).findPublishedRows("spring", "boot", "java", "query");
    }

    /** 페이지의 글 수와 전체 건수를 분리하고 한 글의 전체 태그를 병합한다. */
    @Test
    void mapsPublishedPostPageWithoutCountingTagsAsPosts() {
        UUID id = UUID.randomUUID();
        when(repository.findPublishedPage("dev", null, List.of("java", "spring"), "query", 20, 20))
                .thenReturn(new PublishedPage(List.of(
                        row(id, "page-post", "Page Post", "요약", "본문", Instant.parse("2026-08-31T00:00:00Z"), "Java"),
                        row(id, "page-post", "Page Post", "요약", "본문", Instant.parse("2026-08-31T00:00:00Z"), "Spring")), 21));

        var page = service.findPage("dev", null, List.of("java", "spring"), "query", 2, 20);
        assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().tags()).containsExactly("Java", "Spring");
        verify(repository).findPublishedPage("dev", null, List.of("java", "spring"), "query", 20, 20);
    }

    /** 태그 병합 후에도 실제 시리즈명과 저장된 연재 번호가 화면에 전달된다. */
    @Test
    void preservesSeriesMetadataWhenMergingTagRows() {
        UUID id = UUID.randomUUID();
        when(repository.findPublishedRows("dev", "spring-blog", "java", null)).thenReturn(List.of(
                new PublicPostRow(id, "third", "세 번째 글", "요약", "본문", Instant.parse("2026-01-01T00:00:00Z"),
                        "Java", 3, null, "개발", "spring-blog", "Spring 블로그"),
                new PublicPostRow(id, "third", "세 번째 글", "요약", "본문", Instant.parse("2026-01-01T00:00:00Z"),
                        "Spring", 3, null, "개발", "spring-blog", "Spring 블로그")));
        var views = service.findAll("dev", "spring-blog", "java", null);
        assertThat(views).hasSize(1);
        var view = views.getFirst();
        assertThat(view.categoryName()).isEqualTo("개발");
        assertThat(view.seriesSlug()).isEqualTo("spring-blog");
        assertThat(view.seriesName()).isEqualTo("Spring 블로그");
        assertThat(view.seriesOrder()).isEqualTo(3);
        assertThat(view.tags()).containsExactly("Java", "Spring");
    }

    private PublicPostRow row(UUID id, String slug, String title, String summary, String content, Instant publishedAt, String tagName) {
        return new PublicPostRow(id, slug, title, summary, content, publishedAt, tagName);
    }
}
