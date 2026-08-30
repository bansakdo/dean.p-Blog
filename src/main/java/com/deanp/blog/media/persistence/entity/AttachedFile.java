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

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "created_at")
    private Instant createdAt;

}
