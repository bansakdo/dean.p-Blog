package com.deanp.blog.media.persistence.entity;

import jakarta.persistence.Column;
import com.deanp.blog.post.persistence.entity.BoardDetail;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "board_attached_file", schema = "blog")
public class BoardAttachedFile {

    @EmbeddedId
    private BoardAttachedFileId id;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_detail_id", insertable = false, updatable = false)
    private BoardDetail boardDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_file_id", insertable = false, updatable = false)
    private AttachedFile attachedFile;
}
