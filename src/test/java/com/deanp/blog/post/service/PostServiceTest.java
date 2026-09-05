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

    private PublicPostRow row(UUID id, String slug, String title, String summary, String content, Instant publishedAt, String tagName) {
        return new PublicPostRow(id, slug, title, summary, content, publishedAt, tagName);
    }
}
