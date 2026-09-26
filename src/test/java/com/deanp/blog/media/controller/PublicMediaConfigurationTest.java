package com.deanp.blog.media.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공개 게시글 이미지 경로만 외부 저장소에서 제공하는지 검증한다. */
@WebMvcTest(PublicMediaConfigurationTest.EmptyController.class)
@Import(PublicMediaConfiguration.class)
class PublicMediaConfigurationTest {
    @TempDir
    static Path storageRoot;

    @Autowired
    private MockMvc mockMvc;

    /** @param registry 테스트별 외부 저장소 루트 */
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage-root", () -> storageRoot.toString());
    }

    /** 저장 규약 posts/{글 ID}/{파일명}과 공개 URL이 일치한다. */
    @Test
    void servesStoredPostImageWithoutSecondMediaDirectory() throws Exception {
        UUID postId = UUID.randomUUID();
        Path image = storageRoot.resolve("posts").resolve(postId.toString()).resolve("cover.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[] {1, 2, 3});

        mockMvc.perform(get("/media/posts/{postId}/cover.png", postId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    /** 게시글 외부의 파일이나 없는 파일은 공개하지 않는다. */
    @Test
    void doesNotExposeUnrelatedFiles() throws Exception {
        Files.writeString(storageRoot.resolve("private.txt"), "private");
        Files.createDirectories(storageRoot.resolve("media/posts"));
        Files.writeString(storageRoot.resolve("media/posts/old.txt"), "old");

        mockMvc.perform(get("/media/private.txt")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/posts/old.txt")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/posts/missing.png")).andExpect(status().isNotFound());
    }

    /** 미디어 리소스 검증용 요청 없는 MVC 경계다. */
    @Controller
    static class EmptyController {
    }
}
