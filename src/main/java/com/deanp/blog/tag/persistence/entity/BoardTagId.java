package com.deanp.blog.tag.persistence.entity;

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
public class BoardTagId {
    @Column(name = "board_detail_id")
    private UUID boardDetailId;
    @Column(name = "tag_id")
    private UUID tagId;
}
