package com.deanp.blog.post.persistence.query;

import java.time.Instant;
import java.util.UUID;

/**
 * 공개 게시글 조회 결과의 한 행과 선택된 태그명을 담는다.
 *
 * @param id 게시글 식별자
 * @param slug 게시글 슬러그
 * @param title 게시글 제목
 * @param summary 게시글 요약
 * @param content Markdown 원문 본문
 * @param publishedAt 게시글 발행 시각
 * @param tagName 연결된 태그명
 */
public record PublicPostRow(
        UUID id,
        String slug,
        String title,
        String summary,
        String content,
        Instant publishedAt,
        String tagName
) {
}
