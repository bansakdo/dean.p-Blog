package com.deanp.blog.post.service;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.persistence.query.PublicPostRow;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 게시글 조회 결과를 화면 표시용 모델로 변환하고 Markdown 렌더링을 담당한다.
 */
@Service
public class PostService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");
    private static final int READING_CHARS_PER_MINUTE = 500;

    private final PostDetailRepository posts;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    /**
     * 게시글 저장소와 안전한 Markdown 렌더러를 준비한다.
     *
     * @param posts 공개 게시글 조회를 제공하는 저장소
     */
    public PostService(PostDetailRepository posts) {
        this.posts = posts;

        // 사용자 콘텐츠의 HTML 실행을 막도록 Markdown 렌더러 옵션을 고정한다.
        MutableDataSet options = new MutableDataSet()
                .set(HtmlRenderer.ESCAPE_HTML, true)
                .set(HtmlRenderer.ESCAPE_INLINE_HTML, true);

        // 동일한 옵션으로 파서와 렌더러를 구성해 이후 변환에서 재사용한다.
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    /**
     * 모든 공개 글을 화면 표시 모델로 조회한다.
     *
     * @return 최신순 공개 글 목록
     */
    public List<PostView> findAll() {
        return findAll(null);
    }

    /**
     * 카테고리 조건에 맞는 공개 글을 화면 표시 모델로 조회한다.
     *
     * @param categorySlug 카테고리 슬러그, 비어 있으면 전체 공개 글
     * @return 최신순 공개 글 목록
     */
    public List<PostView> findAll(String categorySlug) {
        return toPostViews(posts.findPublishedRowsNewestFirst(categorySlug));
    }

    /**
     * 슬러그에 해당하는 공개 글을 화면 표시 모델로 조회한다.
     *
     * @param slug 게시글 슬러그
     * @return 게시글이 존재할 때의 표시 모델
     */
    public Optional<PostView> findBySlug(String slug) {
        List<PostView> matches = toPostViews(posts.findPublishedRowsBySlug(slug));
        return matches.stream().findFirst();
    }

    /**
     * 태그 조인으로 중복된 게시글 행을 슬러그 기준으로 묶어 표시 모델로 변환한다.
     *
     * @param rows 게시글과 태그명이 함께 담긴 조회 행
     * @return 게시글 단위로 병합된 표시 모델 목록
     */
    private List<PostView> toPostViews(List<PublicPostRow> rows) {
        // 조회 순서를 보존하면서 같은 슬러그의 행을 하나의 누적 객체로 묶는다.
        Map<String, PostAccumulator> grouped = new LinkedHashMap<>();
        for (PublicPostRow row : rows) {
            grouped.computeIfAbsent(row.slug(), ignored -> new PostAccumulator(row)).addTag(row.tagName());
        }

        // 누적된 태그 집합을 게시글 표시 모델로 변환한다.
        return grouped.values().stream().map(PostAccumulator::toView).toList();
    }

    /**
     * 단일 게시글 행과 태그 목록을 화면 표시 모델로 변환한다.
     *
     * @param row 대표 게시글 조회 행
     * @param tags 중복 제거와 순서 보존이 끝난 태그 목록
     * @return Thymeleaf 템플릿에서 사용할 게시글 표시 모델
     */
    private PostView toPostView(PublicPostRow row, List<String> tags) {
        // 본문은 읽기 시간 계산과 Markdown 렌더링에 함께 사용한다.
        String content = row.content();

        // 발행일, 읽기 시간, 태그, 렌더링된 HTML을 한 화면 모델로 조립한다.
        return new PostView(
                row.id(),
                row.slug(),
                row.title(),
                row.summary(),
                LocalDate.ofInstant(row.publishedAt(), DISPLAY_ZONE),
                readingMinutes(content),
                List.copyOf(tags),
                htmlRenderer.render(markdownParser.parse(content))
        );
    }

    /**
     * 공백을 제외한 글자 수로 대략적인 읽기 시간을 계산한다.
     *
     * @param content Markdown 원문 본문
     * @return 최소 1분인 읽기 시간
     */
    private int readingMinutes(String content) {
        // 표시용 읽기 시간에서 공백은 제외하고 실제 문자만 센다.
        long readableCharacters = content.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count();

        // 기준 글자 수로 올림 계산하되 아주 짧은 글도 1분으로 표시한다.
        return Math.max(1, (int) Math.ceil((double) readableCharacters / READING_CHARS_PER_MINUTE));
    }

    /**
     * 태그별 조회 행을 게시글 단위로 병합하기 위한 내부 누적 객체다.
     */
    private final class PostAccumulator {
        private final PublicPostRow row;
        private final LinkedHashSet<String> tags = new LinkedHashSet<>();

        /**
         * 대표 게시글 행을 보관한다.
         *
         * @param row 병합 대상 게시글의 대표 조회 행
         */
        private PostAccumulator(PublicPostRow row) {
            this.row = row;
        }

        /**
         * 비어 있지 않은 태그명을 중복 없이 누적한다.
         *
         * @param tagName 조회 행에 포함된 태그명
         */
        private void addTag(String tagName) {
            if (tagName != null && !tagName.isBlank()) {
                tags.add(tagName);
            }
        }

        /**
         * 누적된 태그와 대표 행을 최종 표시 모델로 변환한다.
         *
         * @return 게시글 단위 표시 모델
         */
        private PostView toView() {
            return PostService.this.toPostView(row, new ArrayList<>(tags));
        }
    }
}
