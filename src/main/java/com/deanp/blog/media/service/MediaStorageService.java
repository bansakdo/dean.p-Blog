package com.deanp.blog.media.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 게시글 미디어 파일을 애플리케이션 외부 저장 경로에 보관한다.
 */
@Service
public class MediaStorageService {

    private final Path storageRoot;

    /**
     * 미디어 저장소를 초기화한다.
     *
     * @param storageRoot 외부 미디어 파일을 저장할 루트 경로
     */
    public MediaStorageService(
            @Value("${app.media.storage-root:./media}") Path storageRoot
    ) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    /**
     * 게시글별 디렉터리에 UUID 기반 파일을 저장한다.
     *
     * @param postDetailId 게시글 상세 식별자
     * @param extension 저장할 파일의 확장자
     * @param content 파일 내용
     * @return 저장된 미디어 파일 정보
     * @throws IOException 파일 저장에 실패한 경우
     */
    public StoredMedia store(UUID postDetailId, String extension, InputStream content) throws IOException {
        UUID fileId = UUID.randomUUID();
        String normalizedExtension = normalizeExtension(extension);
        String storedName = fileId + normalizedExtension;
        Path directory = storageRoot.resolve("posts").resolve(postDetailId.toString()).normalize();
        Path target = directory.resolve(storedName).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new IOException("미디어 저장 경로가 저장소 루트를 벗어났습니다.");
        }

        Files.createDirectories(directory);
        Path realRoot = storageRoot.toRealPath();
        if (!directory.toRealPath().startsWith(realRoot)) {
            throw new IOException("미디어 저장 경로가 저장소 루트를 벗어났습니다.");
        }
        // 직접 생성한 파일만 실패 시 정리하고 기존 파일은 덮어쓰지 않는다.
        Files.createFile(target);
        try {
            try (var output = Files.newOutputStream(target, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                content.transferTo(output);
            }
            return new StoredMedia(fileId, postDetailId, storedName, target, Files.size(target));
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    /**
     * 저장된 미디어 파일을 삭제한다.
     *
     * @param storedMedia 삭제할 미디어 파일 정보
     * @throws IOException 파일 삭제에 실패한 경우
     */
    public void delete(StoredMedia storedMedia) throws IOException {
        Path target = storedMedia.path().toAbsolutePath().normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IOException("미디어 삭제 경로가 저장소 루트를 벗어났습니다.");
        }
        Files.deleteIfExists(target);
    }

    /**
     * 확장자를 파일명에 사용할 수 있는 형태로 정규화한다.
     *
     * @param extension 원본 확장자
     * @return 점을 포함한 정규화 확장자
     */
    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String normalized = extension.startsWith(".") ? extension : "." + extension;
        return normalized.toLowerCase().replaceAll("[^a-z0-9.]", "");
    }

    /**
     * 저장된 미디어 파일의 식별 정보다.
     *
     * @param fileId 파일 식별자
     * @param postDetailId 게시글 상세 식별자
     * @param storedName 저장된 파일명
     * @param path 저장된 절대 경로
     * @param fileSize 저장된 파일 크기
     */
    public record StoredMedia(
            UUID fileId,
            UUID postDetailId,
            String storedName,
            Path path,
            long fileSize
    ) {
    }
}
