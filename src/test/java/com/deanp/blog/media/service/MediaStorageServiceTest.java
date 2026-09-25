package com.deanp.blog.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
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
}
