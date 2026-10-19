package com.deanp.blog.media.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 외부 저장소에 보관된 첨부 파일 메타데이터를 blog.attached_files 테이블에 매핑한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "attached_files", schema = "blog")
public class AttachedFile {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "stored_name")
    private String storedName;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum", length = 64, columnDefinition = "char(64)")
    private String checksum;

    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * 이미지 첨부인지 확인한다. 형식을 기록하지 않은 예전 첨부는 이미지로 본다.
     *
     * @return 이미지 첨부 여부
     */
    public boolean isImage() {
        return contentType == null || contentType.startsWith("image/");
    }

    /**
     * 외부 저장소에 저장을 마친 파일의 메타데이터를 만든다.
     *
     * @param id 파일 식별자
     * @param originalName 업로드한 원본 파일명
     * @param storedName 저장소에 쓴 파일명
     * @param storagePath 저장소 루트 기준 상대 경로
     * @param contentType MIME 타입
     * @param fileSize 파일 크기(바이트)
     * @param checksum SHA-256 16진수 값
     * @return 저장할 첨부 파일 메타데이터
     */
    public static AttachedFile create(UUID id, String originalName, String storedName, String storagePath,
                                      String contentType, long fileSize, String checksum) {
        AttachedFile file = new AttachedFile();
        file.id = id;
        file.originalName = originalName;
        file.storedName = storedName;
        file.storagePath = storagePath;
        file.contentType = contentType;
        file.fileSize = fileSize;
        file.checksum = checksum;
        file.createdAt = Instant.now();
        return file;
    }
}
