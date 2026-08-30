package com.deanp.blog.media.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardAttachedFileId {
    @Column(name = "board_detail_id")
    private UUID boardDetailId;
    @Column(name = "attached_file_id")
    private UUID attachedFileId;
}
