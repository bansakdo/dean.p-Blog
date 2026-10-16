package com.deanp.blog.media.service;

import com.deanp.blog.media.persistence.entity.AttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;
import com.deanp.blog.media.persistence.repository.AttachedFileRepository;
import com.deanp.blog.media.persistence.repository.PostAttachedFileRepository;
import com.deanp.blog.media.service.MediaImageValidationService.ValidatedImage;
import com.deanp.blog.media.service.MediaStorageService.StoredMedia;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 게시글 이미지를 검증해 외부 저장소에 두고 게시글의 첨부 파일로 기록한다.
 */
@Service
@RequiredArgsConstructor
public class PostImageService {

    private final MediaImageValidationService validation;
    private final MediaStorageService storage;
    private final AttachedFileRepository attachedFiles;
    private final PostAttachedFileRepository postAttachedFiles;
    private final PostDetailRepository posts;

    /**
     * 이미지를 저장하고 게시글에 첨부한다.
     *
     * @param postDetailId 이미지를 첨부할 게시글 식별자
     * @param file 업로드한 이미지 파일
     * @return 본문에 넣을 수 있는 이미지 정보
     */
    @Transactional
    public PostImage upload(UUID postDetailId, MultipartFile file) {
        if (!posts.existsById(postDetailId)) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }

        // 형식과 크기를 검증한 뒤에만 외부 저장소에 파일을 쓴다.
        ValidatedImage image = validation.validate(file);
        StoredMedia stored;
        try {
            stored = storage.store(postDetailId, image.extension(), new ByteArrayInputStream(image.content()));
        } catch (IOException exception) {
            throw new UncheckedIOException("이미지를 저장하지 못했습니다.", exception);
        }

        // 메타데이터 기록에 실패하면 방금 쓴 파일을 지워 주인 없는 파일을 남기지 않는다.
        try {
            attachedFiles.saveAndFlush(AttachedFile.create(
                    stored.fileId(),
                    file.getOriginalFilename(),
                    stored.storedName(),
                    "posts/" + postDetailId + "/" + stored.storedName(),
                    image.contentType(),
                    stored.fileSize(),
                    sha256(image.content())
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

        return new PostImage(stored.fileId(), file.getOriginalFilename(), url(postDetailId, stored.storedName()));
    }

    /**
     * @param postDetailId 게시글 식별자
     * @return 게시글에 첨부된 이미지를 첨부 순서대로 나열한 목록(다이어그램 등 이미지가 아닌 첨부는 뺀다)
     */
    @Transactional(readOnly = true)
    public List<PostImage> findByPost(UUID postDetailId) {
        return postAttachedFiles.findByIdPostDetailIdOrderBySortOrderAsc(postDetailId).stream()
                .filter(link -> link.getAttachedFile().isImage())
                .map(link -> new PostImage(
                        link.getAttachedFile().getId(),
                        link.getAttachedFile().getOriginalName(),
                        url(postDetailId, link.getAttachedFile().getStoredName())))
                .toList();
    }

    /**
     * @param postDetailId 게시글 식별자
     * @param storedName 저장소에 쓴 파일명
     * @return 공개 미디어 경로
     */
    private String url(UUID postDetailId, String storedName) {
        return "/media/posts/" + postDetailId + "/" + storedName;
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
     * 게시글에 첨부된 이미지 한 장의 표시 정보다.
     *
     * @param id 첨부 파일 식별자
     * @param name 업로드한 원본 파일명
     * @param url 공개 미디어 경로
     */
    public record PostImage(UUID id, String name, String url) {
    }
}
