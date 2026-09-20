package com.deanp.blog.media.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 이미지의 파일명, 형식, 크기, 실제 이미지 데이터를 검증한다.
 */
@Service
public class MediaImageValidationService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8_000;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    /**
     * 업로드 파일을 검증하고 저장에 필요한 정보를 반환한다.
     *
     * @param file 업로드 파일
     * @return 검증된 이미지 정보
     */
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("이미지 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new MediaValidationException("이미지 파일은 10MB를 초과할 수 없습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        if (originalFilename == null || originalFilename.isBlank()
                || originalFilename.contains("/") || originalFilename.contains("\\")
                || originalFilename.contains(":") || originalFilename.chars().anyMatch(Character::isISOControl)) {
            throw new MediaValidationException("파일명이 올바르지 않습니다.");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new MediaValidationException("지원하지 않는 이미지 확장자입니다.");
        }

        String contentType = file.getContentType();
        if (!CONTENT_TYPES.get(extension).equalsIgnoreCase(contentType)) {
            throw new MediaValidationException("파일 확장자와 MIME 타입이 일치하지 않습니다.");
        }

        try (var source = file.getInputStream()) {
            // 요청 메타데이터를 신뢰하지 않고 실제 읽는 바이트 수를 제한한다.
            byte[] bytes = source.readNBytes((int) MAX_FILE_SIZE + 1);
            if (bytes.length > MAX_FILE_SIZE) {
                throw new MediaValidationException("이미지 파일은 10MB를 초과할 수 없습니다.");
            }
            try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new MediaValidationException("읽을 수 없는 이미지 파일입니다.");
                }
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String actual = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String expected = extension.equals("jpg") ? "jpeg" : extension;
                    if (!actual.equals(expected)) {
                        throw new MediaValidationException("실제 이미지 형식과 확장자/MIME 타입이 일치하지 않습니다.");
                    }
                    // 픽셀 배열을 할당하기 전에 헤더 크기와 총 픽셀 수를 제한한다.
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                            || (long) width * height > 16_000_000L) {
                        throw new MediaValidationException("이미지 해상도는 각 8000px, 총 1600만 픽셀 이하만 허용됩니다.");
                    }
                    reader.addIIOReadWarningListener((ignored, warning) -> {
                        throw new MediaValidationException("손상된 이미지 파일입니다.");
                    });
                    BufferedImage image = reader.read(0);
                    if (image == null) {
                        throw new MediaValidationException("읽을 수 없는 이미지 파일입니다.");
                    }
                    image.flush();
                    return new ValidatedImage(extension, CONTENT_TYPES.get(extension), bytes.length, width, height, bytes);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw new MediaValidationException("이미지 파일을 읽을 수 없습니다.", exception);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 검증된 이미지 정보다.
     */
    public record ValidatedImage(
            String extension,
            String contentType,
            long fileSize,
            int width,
            int height,
            byte[] content
    ) {
    }

    /**
     * 이미지 검증 실패를 나타낸다.
     */
    public static class MediaValidationException extends RuntimeException {
        public MediaValidationException(String message) {
            super(message);
        }

        public MediaValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
