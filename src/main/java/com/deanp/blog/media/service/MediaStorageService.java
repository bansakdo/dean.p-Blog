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

    /** 공개 정적 경로로 내보내는 게시글 이미지 영역 */
    public static final String POSTS_AREA = "posts";
    /** 격리 헤더를 붙여 컨트롤러로만 내보내는 Archify 다이어그램 영역 */
    public static final String DIAGRAMS_AREA = "diagrams";
    /** 공개 여부를 확인한 뒤 다운로드로만 내보내는 첨부 파일 영역 */
    public static final String FILES_AREA = "files";

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
        return store(POSTS_AREA, postDetailId, extension, content);
    }

    /**
     * 저장 영역 아래 게시글별 디렉터리에 UUID 기반 파일을 저장한다.
     * 공개 정적 경로(/media/posts/**)로 바로 내보내면 안 되는 파일은 posts가 아닌 영역에 둔다.
     *
     * @param area 저장소 루트 아래 최상위 디렉터리 이름(posts, diagrams, files)
     * @param postDetailId 게시글 상세 식별자
     * @param extension 저장할 파일의 확장자
     * @param content 파일 내용
     * @return 저장된 미디어 파일 정보
     * @throws IOException 파일 저장에 실패한 경우
     */
    public StoredMedia store(String area, UUID postDetailId, String extension, InputStream content) throws IOException {
        if (!POSTS_AREA.equals(area) && !DIAGRAMS_AREA.equals(area) && !FILES_AREA.equals(area)) {
            throw new IllegalArgumentException("알 수 없는 미디어 저장 영역입니다.");
        }
        UUID fileId = UUID.randomUUID();
        String normalizedExtension = normalizeExtension(extension);
        String storedName = fileId + normalizedExtension;
        Path directory = storageRoot.resolve(area).resolve(postDetailId.toString()).normalize();
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
     * 저장소 루트 기준 상대 경로를 실제 파일 경로로 바꾼다. 루트를 벗어나는 경로는 거부한다.
     *
     * @param storagePath 첨부 파일 메타데이터의 상대 경로
     * @return 저장소 안의 절대 경로
     * @throws IOException 경로가 저장소 루트를 벗어난 경우
     */
    public Path resolve(String storagePath) throws IOException {
        Path target = storageRoot.resolve(storagePath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IOException("미디어 경로가 저장소 루트를 벗어났습니다.");
        }
        return target;
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
     * 첨부 파일 메타데이터의 상대 경로로 저장된 파일을 삭제한다.
     *
     * @param storagePath 첨부 파일 메타데이터의 상대 경로
     * @throws IOException 경로가 저장소 루트를 벗어났거나 삭제에 실패한 경우
     */
    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(resolve(storagePath));
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
