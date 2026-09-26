package com.deanp.blog.media.controller;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 게시글 이미지의 공개 URL을 외부 미디어 저장소에 연결한다. */
@Configuration
public class PublicMediaConfiguration implements WebMvcConfigurer {
    private final Path storageRoot;

    /** @param storageRoot 게시글 이미지를 저장하는 외부 디렉터리 */
    public PublicMediaConfiguration(@Value("${app.media.storage-root:./media}") Path storageRoot) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    /** @param registry 게시글 이미지 경로만 공개할 MVC 리소스 등록기 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storageRoot.resolve("posts").toUri().toString();
        registry.addResourceHandler("/media/posts/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
