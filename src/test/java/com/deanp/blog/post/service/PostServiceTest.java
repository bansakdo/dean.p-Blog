package com.deanp.blog.post.service;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.persistence.query.PublicPostRow;
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
