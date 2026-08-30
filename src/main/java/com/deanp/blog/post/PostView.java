package com.deanp.blog.post;

import java.time.LocalDate;
import java.util.List;

public record PostView(
        String slug,
        String title,
        String summary,
        LocalDate publishedAt,
        int readingMinutes,
        List<String> tags,
        List<String> paragraphs
) {
}
