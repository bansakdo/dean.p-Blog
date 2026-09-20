package com.deanp.blog.post.service;

import com.deanp.blog.post.persistence.query.PublicPostRow;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RepresentativeImageTest {
    @Test void explicitImageIsIndependentOfMarkdown() {
        var repository = mock(PostDetailRepository.class);
        var id = UUID.randomUUID();
        when(repository.findPublishedRowsNewestFirst(null)).thenReturn(List.of(
            new PublicPostRow(id, "sample", "title", "summary", "![body](/other.jpeg)", Instant.now(), null, "chosen.jpeg")));
        var view = new PostService(repository).findAll().getFirst();
        assertThat(view.representativeImageUrl()).isEqualTo("/media/posts/" + id + "/chosen.jpeg");
        assertThat(view.htmlContent()).contains("/other.jpeg");
    }
    @Test void bodyImageDoesNotBecomeThumbnailAutomatically() {
        var repository = mock(PostDetailRepository.class);
        when(repository.findPublishedRowsNewestFirst(null)).thenReturn(List.of(
            new PublicPostRow(UUID.randomUUID(), "sample", "title", "summary", "![body](/other.jpeg)", Instant.now(), null)));
        assertThat(new PostService(repository).findAll().getFirst().representativeImageUrl()).isNull();
    }
}
