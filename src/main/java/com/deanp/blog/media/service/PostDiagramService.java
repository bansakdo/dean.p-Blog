package com.deanp.blog.media.service;

import com.deanp.blog.media.persistence.entity.AttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;
import com.deanp.blog.media.persistence.repository.AttachedFileRepository;
import com.deanp.blog.media.persistence.repository.PostAttachedFileRepository;
import com.deanp.blog.media.service.MediaImageValidationService.MediaValidationException;
import com.deanp.blog.media.service.MediaStorageService.StoredMedia;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Archify가 만든 인터랙티브 다이어그램 HTML을 검증해 공개 정적 경로 밖에 보관하고 게시글 첨부로 기록한다.
 * 다이어그램은 스크립트를 담고 있으므로 {@code /media/diagrams/{id}}에서 격리 헤더와 함께만 내보낸다.
 */
@Service
@RequiredArgsConstructor
public class PostDiagramService {

    /** 첨부 파일 메타데이터에 기록하는 다이어그램 MIME 타입 */
    public static final String CONTENT_TYPE = "text/html";
    /** 다이어그램 HTML 최대 크기(5MB). Archify 결과물은 보통 1MB 안팎이다. */
    static final long MAX_SIZE = 5L * 1024 * 1024;
    /**
     * Archify가 head에 넣는 생성기 표시. 다른 파일을 잘못 올리는 실수를 막는 확인일 뿐, 보안 장치는 아니다.
     * 다이어그램 격리는 {@code DiagramController}의 CSP sandbox가 맡는다.
     */
    private static final Pattern GENERATOR = Pattern.compile(
            "<meta\\s+name=[\"']generator[\"']\\s+content=[\"']archify\\s+[0-9][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    /** 생성기 표시를 찾을 파일 앞부분 길이 */
    private static final int GENERATOR_SCAN_LIMIT = 4096;

    private final MediaStorageService storage;
    private final AttachedFileRepository attachedFiles;
    private final PostAttachedFileRepository postAttachedFiles;
    private final PostDetailRepository posts;

    /**
     * 다이어그램 HTML을 저장하고 게시글에 첨부한다.
     *
     * @param postDetailId 다이어그램을 첨부할 게시글 식별자
     * @param file 업로드한 Archify HTML 파일
     * @return 본문에 넣을 수 있는 다이어그램 정보
     */
    @Transactional
    public PostDiagram upload(UUID postDetailId, MultipartFile file) {
        if (!posts.existsById(postDetailId)) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }

        // Archify 결과물인지 확인한 뒤에만 저장소에 쓴다.
        byte[] content = validate(file);
        StoredMedia stored;
        try {
            stored = storage.store(MediaStorageService.DIAGRAMS_AREA, postDetailId, ".html", new ByteArrayInputStream(content));
        } catch (IOException exception) {
            throw new UncheckedIOException("다이어그램을 저장하지 못했습니다.", exception);
        }

        // 메타데이터 기록에 실패하면 방금 쓴 파일을 지워 주인 없는 파일을 남기지 않는다.
        try {
            attachedFiles.saveAndFlush(AttachedFile.create(
                    stored.fileId(),
                    file.getOriginalFilename(),
                    stored.storedName(),
                    MediaStorageService.DIAGRAMS_AREA + "/" + postDetailId + "/" + stored.storedName(),
                    CONTENT_TYPE,
                    stored.fileSize(),
                    sha256(content)
            ));
            postAttachedFiles.saveAndFlush(PostAttachedFile.create(
                    new PostAttachedFileId(postDetailId, stored.fileId()),
                    postAttachedFiles.countByIdPostDetailId(postDetailId)
            ));
        } catch (RuntimeException exception) {
            try {
                storage.delete(stored);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
        return new PostDiagram(stored.fileId(), file.getOriginalFilename());
    }

    /**
     * 게시글에 첨부된 다이어그램을 첨부 순서대로 나열한다.
     *
     * @param postDetailId 게시글 식별자
     * @return 다이어그램 목록
     */
    @Transactional(readOnly = true)
    public List<PostDiagram> findByPost(UUID postDetailId) {
        return postAttachedFiles.findByIdPostDetailIdOrderBySortOrderAsc(postDetailId).stream()
                .map(PostAttachedFile::getAttachedFile)
                .filter(PostDiagramService::isDiagram)
                .map(file -> new PostDiagram(file.getId(), file.getOriginalName()))
                .toList();
    }

    /**
     * 공개 경로로 내보낼 다이어그램 파일 위치와 체크섬을 찾는다. 다이어그램이 아니거나 파일이 없으면 빈 값이다.
     * 파일 내용은 트랜잭션 밖에서 응답으로 흘려보내도록 위치만 돌려준다.
     *
     * @param fileId 첨부 파일 식별자
     * @return 다이어그램 파일 정보 또는 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<DiagramFile> find(UUID fileId) {
        return attachedFiles.findById(fileId)
                .filter(PostDiagramService::isDiagram)
                .map(file -> {
                    try {
                        return new DiagramFile(storage.resolve(file.getStoragePath()), file.getChecksum());
                    } catch (IOException exception) {
                        throw new UncheckedIOException("다이어그램 경로가 올바르지 않습니다.", exception);
                    }
                })
                .filter(file -> Files.isRegularFile(file.path()));
    }

    /**
     * @param file 첨부 파일 메타데이터
     * @return diagrams 영역에 저장한 HTML 첨부인지 여부
     */
    private static boolean isDiagram(AttachedFile file) {
        return CONTENT_TYPE.equals(file.getContentType())
                && file.getStoragePath().startsWith(MediaStorageService.DIAGRAMS_AREA + "/");
    }

    /**
     * 확장자, 크기, UTF-8 여부, Archify 생성 표시를 확인한다.
     *
     * @param file 업로드한 파일
     * @return 검증한 파일 내용
     */
    private byte[] validate(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".html")) {
            throw new MediaValidationException("Archify로 만든 .html 파일만 올릴 수 있습니다.");
        }
        if (file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new MediaValidationException("다이어그램은 5MB 이하여야 합니다.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new MediaValidationException("파일을 읽지 못했습니다.", exception);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw new MediaValidationException("UTF-8 HTML 파일만 올릴 수 있습니다.", exception);
        }
        // Archify 결과물은 head 앞부분에 <meta name="generator" content="archify x.y.z">를 넣는다.
        if (!GENERATOR.matcher(text.substring(0, Math.min(text.length(), GENERATOR_SCAN_LIMIT))).find()) {
            throw new MediaValidationException("Archify로 만든 HTML이 아닙니다. archify render/deliver 결과 파일을 올려 주세요.");
        }
        return content;
    }

    /**
     * @param content 파일 내용
     * @return SHA-256 16진수 문자열
     */
    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    /**
     * 내보낼 다이어그램 파일이다.
     *
     * @param path 저장소 안의 절대 경로
     * @param checksum 내용의 SHA-256(ETag로 쓴다), 없으면 null
     */
    public record DiagramFile(Path path, String checksum) {
    }

    /**
     * 게시글에 첨부된 다이어그램 하나의 표시 정보다.
     *
     * @param id 첨부 파일 식별자
     * @param name 업로드한 원본 파일명
     */
    public record PostDiagram(UUID id, String name) {

        /** @return 본문에 넣는 Markdown 블록 */
        public String markdown() {
            return "```archify\n" + id + "\n```";
        }
    }
}
