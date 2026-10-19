package com.deanp.blog.media.service;

import com.deanp.blog.media.persistence.entity.AttachedFile;
import com.deanp.blog.media.persistence.repository.AttachedFileRepository;
import com.deanp.blog.media.persistence.repository.PostAttachedFileRepository;
import com.deanp.blog.media.service.MediaImageValidationService.MediaValidationException;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Archify 다이어그램 HTML의 검증, 저장 위치, 읽기 범위를 확인한다. */
class PostDiagramServiceTest {

    private static final UUID POST_ID = new UUID(0, 7);
    private static final String ARCHIFY_HTML = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="UTF-8">
            <meta name="generator" content="archify 3.1.0">
            <title>다이어그램</title></head><body><script>1</script></body></html>
            """;

    @TempDir
    Path root;

    private final AttachedFileRepository attachedFiles = mock(AttachedFileRepository.class);
    private final PostAttachedFileRepository postAttachedFiles = mock(PostAttachedFileRepository.class);
    private final PostDetailRepository posts = mock(PostDetailRepository.class);
    private PostDiagramService service;

    /** 실제 임시 저장소와 게시글이 있다고 답하는 저장소를 준비한다. */
    @BeforeEach
    void setUp() {
        service = new PostDiagramService(new MediaStorageService(root), attachedFiles, postAttachedFiles, posts);
        when(posts.existsById(POST_ID)).thenReturn(true);
        when(attachedFiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Archify HTML은 공개 정적 경로(posts) 밖 diagrams 영역에 text/html 첨부로 기록하고, 본문 블록을 돌려준다. */
    @Test
    void storesArchifyHtmlOutsidePublicPostsArea() throws Exception {
        var diagram = service.upload(POST_ID, html("flow.html", ARCHIFY_HTML));

        ArgumentCaptor<AttachedFile> saved = ArgumentCaptor.forClass(AttachedFile.class);
        verify(attachedFiles).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContentType()).isEqualTo("text/html");
        assertThat(saved.getValue().getStoragePath()).isEqualTo("diagrams/" + POST_ID + "/" + diagram.id() + ".html");
        assertThat(Files.readString(root.resolve(saved.getValue().getStoragePath()))).isEqualTo(ARCHIFY_HTML);
        assertThat(Files.exists(root.resolve("posts"))).isFalse();
        assertThat(diagram.markdown()).isEqualTo("```archify\n" + diagram.id() + "\n```");
    }

    /** 확장자, Archify 생성 표시, UTF-8, 크기가 맞지 않으면 저장하지 않는다. */
    @Test
    void rejectsFilesThatAreNotArchifyHtml() {
        assertThatThrownBy(() -> service.upload(POST_ID, html("flow.htm", ARCHIFY_HTML)))
                .isInstanceOf(MediaValidationException.class).hasMessageContaining(".html");
        assertThatThrownBy(() -> service.upload(POST_ID, html("page.html", "<html><head><title>x</title></head></html>")))
                .isInstanceOf(MediaValidationException.class).hasMessageContaining("Archify로 만든 HTML이 아닙니다");
        assertThatThrownBy(() -> service.upload(POST_ID, new MockMultipartFile("file", "bad.html", "text/html",
                new byte[] {'<', (byte) 0xC3, (byte) 0x28})))
                .isInstanceOf(MediaValidationException.class).hasMessageContaining("UTF-8");
        byte[] large = new byte[(int) PostDiagramService.MAX_SIZE + 1];
        assertThatThrownBy(() -> service.upload(POST_ID, new MockMultipartFile("file", "big.html", "text/html", large)))
                .isInstanceOf(MediaValidationException.class).hasMessageContaining("5MB");

        verify(attachedFiles, never()).saveAndFlush(any());
        assertThat(root.toFile().list()).isEmpty();
    }

    /** 없는 글에는 올릴 수 없다. */
    @Test
    void rejectsUnknownPost() {
        assertThatThrownBy(() -> service.upload(new UUID(0, 99), html("flow.html", ARCHIFY_HTML)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 찾기는 diagrams 영역에 실제 파일이 있는 HTML 첨부만 돌려주고, 이미지 첨부·없는 식별자·사라진 파일은 빈 값이다. */
    @Test
    void readsOnlyDiagramAttachments() throws Exception {
        var diagram = service.upload(POST_ID, html("flow.html", ARCHIFY_HTML));
        ArgumentCaptor<AttachedFile> saved = ArgumentCaptor.forClass(AttachedFile.class);
        verify(attachedFiles).saveAndFlush(saved.capture());
        when(attachedFiles.findById(diagram.id())).thenReturn(Optional.of(saved.getValue()));
        UUID imageId = new UUID(0, 8);
        Files.createDirectories(root.resolve("posts"));
        Files.writeString(root.resolve("posts/a.png"), "png");
        when(attachedFiles.findById(imageId)).thenReturn(Optional.of(
                AttachedFile.create(imageId, "a.png", "a.png", "posts/a.png", "image/png", 3, null)));

        assertThat(service.find(diagram.id())).hasValueSatisfying(file -> {
            assertThat(file.path()).isEqualTo(root.resolve(saved.getValue().getStoragePath()));
            assertThat(file.checksum()).hasSize(64);
        });
        assertThat(service.find(imageId)).isEmpty();
        assertThat(service.find(new UUID(0, 9))).isEmpty();
        Files.delete(root.resolve(saved.getValue().getStoragePath()));
        assertThat(service.find(diagram.id())).isEmpty();
    }

    /**
     * @param name 파일명
     * @param content 내용
     * @return 업로드 파일
     */
    private static MockMultipartFile html(String name, String content) {
        return new MockMultipartFile("file", name, "text/html", content.getBytes(StandardCharsets.UTF_8));
    }
}
