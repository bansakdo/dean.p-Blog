package com.deanp.blog.media.service;

import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.media.persistence.entity.AttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;
import com.deanp.blog.media.persistence.repository.AttachedFileRepository;
import com.deanp.blog.media.persistence.repository.PostAttachedFileRepository;
import com.deanp.blog.media.service.MediaImageValidationService.MediaValidationException;
import com.deanp.blog.media.service.MediaStorageService.StoredMedia;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 게시글 첨부 파일(PDF, 압축 파일, 문서, 소스 코드 등)을 공개 정적 경로 밖에 보관하고 게시글 첨부로 기록한다.
 * 공개 글의 파일은 누구나, 공개 전·삭제한 글의 파일은 로그인한 작성자만 {@code /media/files/{id}}에서 내려받는다.
 */
@Service
@RequiredArgsConstructor
public class PostFileService {

    private static final Logger log = LoggerFactory.getLogger(PostFileService.class);

    /** 첨부 파일 최대 크기(20MB). 요청 크기 한도(spring.servlet.multipart)도 이에 맞춘다. */
    static final long MAX_SIZE = 20L * 1024 * 1024;
    /** 원본 파일명 최대 길이 */
    private static final int MAX_NAME_LENGTH = 200;
    /** 공개 화면에 나오는 상태 */
    private static final String PUBLISHED = "PUBLISHED";
    /**
     * 올릴 수 있는 확장자와 기록할 MIME 타입. 브라우저가 보낸 타입은 믿지 않고 확장자로 정한다.
     * 이미지는 이미지 올리기를, HTML은 Archify 다이어그램 올리기를 쓴다. 소스 코드는 text/plain으로 내보낸다.
     */
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("gz", "application/gzip"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("hwp", "application/x-hwp"),
            Map.entry("hwpx", "application/hwp+zip"),
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("java", "text/plain"),
            Map.entry("kt", "text/plain"),
            Map.entry("py", "text/plain"),
            Map.entry("js", "text/plain"),
            Map.entry("ts", "text/plain"),
            Map.entry("sql", "text/plain"),
            Map.entry("yml", "text/plain"),
            Map.entry("yaml", "text/plain"),
            Map.entry("sh", "text/plain")
    );
    /** 업로드 화면 file input의 accept 값 */
    public static final String ACCEPT = String.join(",", CONTENT_TYPES.keySet().stream().sorted().map(ext -> "." + ext).toList());

    private final MediaStorageService storage;
    private final AttachedFileRepository attachedFiles;
    private final PostAttachedFileRepository postAttachedFiles;
    private final PostDetailRepository posts;
    private final AppUserRepository users;

    /**
     * 첨부 파일을 저장하고 게시글에 첨부한다.
     *
     * @param postDetailId 파일을 첨부할 게시글 식별자
     * @param file 업로드한 파일
     * @return 본문 링크와 목록에 쓸 첨부 파일 정보
     */
    @Transactional
    public PostFile upload(UUID postDetailId, MultipartFile file) {
        if (!posts.existsById(postDetailId)) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        String name = originalName(file);
        String extension = extension(name);
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new MediaValidationException("올릴 수 없는 파일 형식입니다. PDF, 압축 파일, 문서, 텍스트, 소스 코드 파일만 올릴 수 있습니다.");
        }
        if (file.isEmpty()) {
            throw new MediaValidationException("빈 파일은 올릴 수 없습니다.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new MediaValidationException("첨부 파일은 20MB 이하여야 합니다.");
        }

        // 저장하면서 체크섬을 계산해 큰 파일을 메모리에 올리지 않는다.
        MessageDigest digest = sha256();
        StoredMedia stored;
        try (InputStream content = new DigestInputStream(file.getInputStream(), digest)) {
            stored = storage.store(MediaStorageService.FILES_AREA, postDetailId, "." + extension, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("첨부 파일을 저장하지 못했습니다.", exception);
        }

        // 메타데이터 기록에 실패하면 방금 쓴 파일을 지워 주인 없는 파일을 남기지 않는다.
        try {
            attachedFiles.saveAndFlush(AttachedFile.create(
                    stored.fileId(),
                    name,
                    stored.storedName(),
                    MediaStorageService.FILES_AREA + "/" + postDetailId + "/" + stored.storedName(),
                    contentType,
                    stored.fileSize(),
                    HexFormat.of().formatHex(digest.digest())
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
        return new PostFile(stored.fileId(), name, stored.fileSize());
    }

    /**
     * 게시글에 첨부된 파일을 첨부 순서대로 나열한다. 이미지와 다이어그램은 빼고 첨부 파일 영역의 파일만 담는다.
     *
     * @param postDetailId 게시글 식별자
     * @return 첨부 파일 목록
     */
    @Transactional(readOnly = true)
    public List<PostFile> findByPost(UUID postDetailId) {
        return postAttachedFiles.findByIdPostDetailIdOrderBySortOrderAsc(postDetailId).stream()
                .map(PostAttachedFile::getAttachedFile)
                .filter(PostFileService::isFile)
                .map(file -> new PostFile(file.getId(), file.getOriginalName(), file.getFileSize()))
                .toList();
    }

    /**
     * 내려받을 파일을 찾는다. 공개 글의 파일은 누구나, 그 밖의 글은 로그인한 작성자만 받을 수 있다.
     * 받을 수 없거나 파일이 없으면 빈 값이다. 파일 내용은 트랜잭션 밖에서 응답으로 흘려보내도록 위치만 돌려준다.
     *
     * @param fileId 첨부 파일 식별자
     * @param loginId 로그인한 사용자 아이디, 로그인하지 않았으면 null
     * @return 내려받을 파일 정보 또는 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<DownloadFile> findForDownload(UUID fileId, String loginId) {
        return postAttachedFiles.findFirstByIdAttachedFileId(fileId)
                .filter(link -> isFile(link.getAttachedFile()))
                .filter(link -> isPublic(link.getPostDetail()) || isAuthor(link.getPostDetail(), loginId))
                .map(link -> {
                    AttachedFile file = link.getAttachedFile();
                    try {
                        return new DownloadFile(storage.resolve(file.getStoragePath()), file.getOriginalName(),
                                file.getContentType(), file.getChecksum());
                    } catch (IOException exception) {
                        throw new UncheckedIOException("첨부 파일 경로가 올바르지 않습니다.", exception);
                    }
                })
                .filter(file -> Files.isRegularFile(file.path()));
    }

    /**
     * 게시글에서 첨부 파일을 지운다. 연결과 메타데이터를 지우고, 저장한 파일은 트랜잭션이 끝난 뒤 지운다.
     * 본문에 넣은 링크는 그대로 남으므로 글쓴이가 따로 지워야 한다.
     *
     * @param postDetailId 게시글 식별자
     * @param fileId 첨부 파일 식별자
     * @return 지웠으면 true, 이 글의 첨부 파일이 아니면 false
     */
    @Transactional
    public boolean delete(UUID postDetailId, UUID fileId) {
        Optional<PostAttachedFile> link = postAttachedFiles.findById(new PostAttachedFileId(postDetailId, fileId))
                .filter(found -> isFile(found.getAttachedFile()));
        if (link.isEmpty()) {
            return false;
        }
        String storagePath = link.get().getAttachedFile().getStoragePath();
        postAttachedFiles.delete(link.get());
        attachedFiles.deleteById(fileId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(storagePath);
                } catch (IOException exception) {
                    // 메타데이터는 이미 지웠으므로 파일만 남는다. 다운로드 경로로는 더 이상 닿지 않는다.
                    log.warn("첨부 파일을 지우지 못했습니다: {}", storagePath, exception);
                }
            }
        });
        return true;
    }

    /**
     * @param file 첨부 파일 메타데이터
     * @return 첨부 파일 영역에 저장한 파일인지 여부
     */
    private static boolean isFile(AttachedFile file) {
        return file.getStoragePath().startsWith(MediaStorageService.FILES_AREA + "/");
    }

    /**
     * @param post 게시글
     * @return 공개 화면에 나오는 글(발행했고 발행 시각이 지났음)인지 여부
     */
    private static boolean isPublic(PostDetail post) {
        return PUBLISHED.equals(post.getStatus()) && post.getPublishedAt() != null
                && !post.getPublishedAt().isAfter(Instant.now());
    }

    /**
     * @param post 게시글
     * @param loginId 로그인한 사용자 아이디 또는 null
     * @return 로그인한 사용자가 글의 작성자인지 여부
     */
    private boolean isAuthor(PostDetail post, String loginId) {
        return loginId != null && users.findByLoginId(loginId)
                .map(user -> user.getId().equals(post.getAuthorId()))
                .orElse(false);
    }

    /**
     * 경로를 떼고 제어 문자와 보이지 않는 서식 문자(글자 방향 바꿈 등)를 지운 원본 파일명을 꺼낸다.
     * 글자 방향을 뒤집는 U+202E를 남기면 {@code .sh} 파일 이름이 {@code .pdf}처럼 보여 확장자를 속일 수 있다.
     *
     * @param file 업로드한 파일
     * @return 표시용 원본 파일명
     */
    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new MediaValidationException("파일 이름은 1~200자여야 합니다.");
        }
        return name;
    }

    /**
     * @param name 원본 파일명
     * @return 소문자 확장자, 없으면 빈 문자열
     */
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** @return SHA-256 계산기 */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    /**
     * 내려받을 첨부 파일이다.
     *
     * @param path 저장소 안의 절대 경로
     * @param name 업로드한 원본 파일명(다운로드 파일명으로 쓴다)
     * @param contentType 기록한 MIME 타입
     * @param checksum 내용의 SHA-256(ETag로 쓴다), 없으면 null
     */
    public record DownloadFile(Path path, String name, String contentType, String checksum) {
    }

    /**
     * 게시글에 첨부된 파일 하나의 표시 정보다.
     *
     * @param id 첨부 파일 식별자
     * @param name 업로드한 원본 파일명
     * @param size 파일 크기(바이트)
     */
    public record PostFile(UUID id, String name, long size) {

        /** @return 내려받기 주소 */
        public String url() {
            return "/media/files/" + id;
        }

        /** @return 본문에 넣는 Markdown 링크. 파일명의 Markdown 문법 문자는 이스케이프한다. */
        public String markdown() {
            return "[" + name.replaceAll("([\\\\`*_\\[\\]()<>#!|~&])", "\\\\$1") + "](" + url() + ")";
        }

        /** @return 사람이 읽기 쉬운 크기(예: 1.2 MB) */
        public String displaySize() {
            if (size < 1024) {
                return size + " B";
            }
            if (size < 1024 * 1024) {
                return String.format(Locale.ROOT, "%.0f KB", size / 1024.0);
            }
            return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024));
        }
    }
}
