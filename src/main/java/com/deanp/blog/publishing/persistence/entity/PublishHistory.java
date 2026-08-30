package com.deanp.blog.publishing.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.post.persistence.entity.BoardDetail;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "publish_history", schema = "blog")
public class PublishHistory {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "board_detail_id")
    private UUID boardDetailId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "status")
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_detail_id", insertable = false, updatable = false)
    private BoardDetail boardDetail;
}
