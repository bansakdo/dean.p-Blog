package com.deanp.blog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void 게시글별_디렉터리에_UUID_파일명으로_저장한다() throws Exception {
        MediaStorageService service = new MediaStorageService(tempDirectory);
        UUID postDetailId = UUID.randomUUID();
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);

        MediaStorageService.StoredMedia stored = service.store(
                postDetailId,
                ".PNG",
                new ByteArrayInputStream(content)
        );

        assertThat(stored.fileId()).isNotNull();
        assertThat(stored.storedName()).isEqualTo(stored.fileId() + ".png");
        assertThat(stored.path()).isEqualTo(
                tempDirectory.toAbsolutePath().normalize()
                        .resolve("posts")
                        .resolve(postDetailId.toString())
                        .resolve(stored.storedName())
        );
        assertThat(Files.readAllBytes(stored.path())).isEqualTo(content);
        assertThat(stored.fileSize()).isEqualTo(content.length);
    }

    @Test
    void 저장된_파일을_삭제한다() throws Exception {
        MediaStorageService service = new MediaStorageService(tempDirectory);
        MediaStorageService.StoredMedia stored = service.store(
                UUID.randomUUID(),
                "webp",
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        );

        service.delete(stored);

        assertThat(Files.exists(stored.path())).isFalse();
    }

    /** 다이어그램은 diagrams 영역에 저장하고, 알 수 없는 영역 이름은 거부한다. */
    @Test
    void 지정한_영역에만_저장한다() throws Exception {
        MediaStorageService service = new MediaStorageService(tempDirectory);
        UUID postId = UUID.randomUUID();

        var stored = service.store(MediaStorageService.DIAGRAMS_AREA, postId, ".html",
                new ByteArrayInputStream("<html></html>".getBytes(StandardCharsets.UTF_8)));

        assertThat(stored.path().getParent()).isEqualTo(tempDirectory.resolve("diagrams").resolve(postId.toString()));
        assertThatThrownBy(() -> service.store("../outside", postId, ".html", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 상대 경로는 저장소 안으로만 풀고, 루트를 벗어나는 경로는 거부한다. */
    @Test
    void 저장소_밖_경로는_풀지_않는다() throws Exception {
        MediaStorageService service = new MediaStorageService(tempDirectory);

        assertThat(service.resolve("diagrams/a/b.html")).isEqualTo(tempDirectory.toAbsolutePath().normalize().resolve("diagrams/a/b.html"));
        assertThatThrownBy(() -> service.resolve("../secret")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> service.resolve("diagrams/../../secret")).isInstanceOf(IOException.class);
    }
}
