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

}
