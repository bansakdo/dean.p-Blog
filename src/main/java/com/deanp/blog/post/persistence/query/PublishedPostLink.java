package com.deanp.blog.post.persistence.query;

import java.time.Instant;

/**
 * 사이트맵에 넣을 공개 글 한 편의 주소 정보다.
 *
 * @param slug 게시글 슬러그
 * @param updatedAt 마지막 수정 시각
 */
public record PublishedPostLink(String slug, Instant updatedAt) {
}
