package com.deanp.blog.post.controller;

import com.deanp.blog.media.service.PostFileService;
import com.deanp.blog.post.PostView;
import com.deanp.blog.post.PostFilterOption;
import com.deanp.blog.post.service.PostService;
import com.deanp.blog.visitor.controller.VisitorCookieIdentifier;
import com.deanp.blog.visitor.service.VisitorPostViewTrackingService;
import com.deanp.blog.visitor.service.VisitorRequestMetadataFactory;
import com.deanp.blog.visitor.service.VisitorTrackingRequestPolicy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 공개 블로그 화면 요청을 받아 Thymeleaf 뷰 모델을 구성한다.
 */
@Controller
@RequiredArgsConstructor
public class BlogController {

    private static final Logger log = LoggerFactory.getLogger(BlogController.class);
    private static final List<Integer> PAGE_SIZES = List.of(10, 20, 50);

    private final PostService postService;
    private final SidebarFilterModel sidebarFilters;
    private final PostFileService postFiles;
    private final VisitorCookieIdentifier visitorCookieIdentifier;
    private final VisitorPostViewTrackingService visitorPostViewTrackingService;
    private final VisitorTrackingRequestPolicy visitorTrackingRequestPolicy;
    private final VisitorRequestMetadataFactory visitorRequestMetadataFactory;

    /**
     * 홈 화면에 추천 글과 최근 글 목록을 배치한다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 홈 템플릿 이름
     */
    @GetMapping("/")
    public String home(Model model) {
        // 공개 글 전체를 최신순으로 가져와 첫 글과 나머지 글을 나눌 준비를 한다.
        List<PostView> allPosts = postService.findAll();

        // 홈 템플릿이 필요한 대표 글, 최근 글, 상태 플래그, 제목을 모델에 담는다.
        model.addAttribute("featuredPosts", allPosts.stream().limit(6).toList());
        model.addAttribute("recentPosts", allPosts.stream().skip(1).toList());
        model.addAttribute("hasPublishedPosts", !allPosts.isEmpty());
        model.addAttribute("pageTitle", "dean.p — 개발과 기록");

        return "home";
    }

    /**
     * 공개 글 목록 화면을 선택된 카테고리 조건으로 렌더링한다.
     *
     * @param category 선택된 카테고리 슬러그, 없으면 전체 글
     * @param series 선택된 시리즈 슬러그, 없으면 시리즈 조건 생략
     * @param search 검색어, 없으면 검색 조건 생략
     * @param tag 쉼표로 구분한 선택 태그 슬러그, 없으면 태그 조건 생략
     * @param page 1부터 시작하는 페이지 번호
     * @param size 페이지당 글 수(10, 20, 50)
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 글 목록 템플릿 이름
     */
    @GetMapping("/posts")
    public String posts(@RequestParam(required = false) String category,
                        @RequestParam(required = false) String series,
                        @RequestParam(required = false, name = "q") String search,
                        @RequestParam(required = false) String tag,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size, Model model) {
        if (page < 1 || !PAGE_SIZES.contains(size)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        // 선택된 필터 조건을 정규화하고 해당 페이지의 글을 조회한다.
        category = normalize(category);
        series = normalize(series);
        var selectedTags = selectedTags(tag);
        search = normalize(search);
        var postPage = postService.findPage(category, series, selectedTags, search, page, size);
        int totalPages = Math.max(1, postPage.getTotalPages());
        if (page > totalPages) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // 조회 결과와 사이드바 필터 상태를 목록 템플릿 모델에 담는다.
        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("matchingPostCount", postPage.getTotalElements());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", IntStream.rangeClosed(1, totalPages).boxed().toList());
        sidebarFilters.add(model.asMap(), category, series, selectedTags, search, size);
        model.addAttribute("selectedSeriesInfo", postService.findSeries(category, series).orElse(null));
        model.addAttribute("hasFilters", category != null || series != null || !selectedTags.isEmpty() || search != null);

        // 글 상세 링크에는 선택된 필터만 붙여 상세 화면의 사이드바가 같은 선택을 이어받게 한다.
        model.addAttribute("postLinkQuery", UriComponentsBuilder.newInstance()
                .queryParamIfPresent("category", Optional.ofNullable(category))
                .queryParamIfPresent("series", Optional.ofNullable(series))
                .queryParamIfPresent("tag", Optional.ofNullable(selectedTags.isEmpty() ? null : String.join(",", selectedTags)))
                .queryParamIfPresent("q", Optional.ofNullable(search))
                .build().encode().toUriString());
        model.addAttribute("pageTitle", "글 — dean.p");

        return "posts";
    }

    /**
     * 쉼표로 구분한 태그 파라미터를 중복 없는 슬러그 목록으로 바꾼다.
     *
     * @param tag 쉼표로 구분한 태그 슬러그 문자열 또는 null
     * @return 선택된 태그 슬러그 목록
     */
    private List<String> selectedTags(String tag) {
        return tag == null ? List.of() : Arrays.stream(tag.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
    }

    /**
     * 추가 공개 태그를 스무 개씩 응답한다.
     * @param offset 이미 표시한 태그 수
     * @return 다음 태그 구간과 뒤에 남은 태그 여부
     */
    @GetMapping("/posts/tags/more")
    @ResponseBody
    public TagPage moreTags(@RequestParam(defaultValue = "20") int offset) {
        if (offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var tags = postService.findTags(offset, SidebarFilterModel.TAG_BATCH_SIZE + 1);
        return new TagPage(tags.stream().limit(SidebarFilterModel.TAG_BATCH_SIZE).toList(), tags.size() > SidebarFilterModel.TAG_BATCH_SIZE);
    }

    /** @param tags 요청 구간의 공개 태그 @param hasMore 추가 구간 존재 여부 */
    public record TagPage(List<PostFilterOption> tags, boolean hasMore) { }

    /**
     * 검색어가 있을 때만 공개 글 검색 결과를 표시한다.
     * @param search 제목·요약·본문 검색어
     * @param model 검색 화면 모델
     * @return 검색 템플릿 이름
     */
    @GetMapping("/search")
    public String search(@RequestParam(required = false, name = "q") String search, Model model) {
        search = normalize(search);
        model.addAttribute("search", search);
        model.addAttribute("posts", search == null ? List.of() : postService.findAll(null, null, null, search));
        model.addAttribute("pageTitle", "검색 — dean.p");
        return "search";
    }

    /**
     * 요청 파라미터의 공백 값을 제거하고 빈 값은 null로 통일한다.
     *
     * @param value 요청 파라미터 값
     * @return 정규화된 값 또는 null
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 슬러그로 공개 글 상세 화면을 렌더링하고, 목록에서 넘어온 필터 선택을 사이드바에 유지한다.
     *
     * @param slug 요청 경로에서 전달된 게시글 슬러그
     * @param category 목록에서 선택했던 카테고리 슬러그, 없으면 전체 글
     * @param series 목록에서 선택했던 시리즈 슬러그
     * @param search 목록에서 사용했던 검색어
     * @param tag 목록에서 선택했던 쉼표 구분 태그 슬러그
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param request 방문자 쿠키를 읽을 현재 HTTP 요청
     * @param response 방문자 쿠키를 설정할 현재 HTTP 응답
     * @return 글 상세 템플릿 이름
     */
    @GetMapping("/posts/{slug}")
    public String post(
            @PathVariable String slug,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String series,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(required = false) String tag,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 슬러그에 맞는 공개 글이 없으면 HTTP 404로 응답한다.
        PostView post = postService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 추적 저장은 게시글 본문 응답의 부가 기능이므로 실패해도 본문을 계속 렌더링한다.
        if (visitorTrackingRequestPolicy.shouldTrack(request)) {
            try {
                String anonymousKey = visitorCookieIdentifier.identify(request, response);
                visitorPostViewTrackingService.recordPostView(
                        anonymousKey,
                        post.id(),
                        visitorRequestMetadataFactory.from(request)
                );
            } catch (RuntimeException exception) {
                log.warn("방문 추적을 생략합니다. postDetailId={}, exceptionType={}",
                        post.id(), exception.getClass().getName());
            }
        }

        // 상세 템플릿이 표시할 글과 첨부 파일, 사이드바 필터 상태, 브라우저 제목을 모델에 담는다.
        model.addAttribute("post", post);
        model.addAttribute("attachments", postFiles.findByPost(post.id()));
        sidebarFilters.add(model.asMap(), normalize(category), normalize(series), selectedTags(tag), normalize(search), SidebarFilterModel.DEFAULT_PAGE_SIZE);
        model.addAttribute("pageTitle", post.title() + " — dean.p");

        // 검색 결과와 공유 미리보기에 글 요약과 대표 이미지를 쓴다. 요약이 없으면 사이트 기본 설명을 유지한다.
        if (post.summary() != null && !post.summary().isBlank()) {
            model.addAttribute("metaDescription", post.summary().strip());
        }
        model.addAttribute("ogType", "article");
        model.addAttribute("ogImage", post.representativeImageUrl());

        return "post";
    }

    /**
     * 소개 화면의 정적 뷰 모델을 구성한다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 소개 템플릿 이름
     */
    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitle", "소개 — dean.p");
        return "about";
    }
}
