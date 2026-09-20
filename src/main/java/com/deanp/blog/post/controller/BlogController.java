package com.deanp.blog.post.controller;

import com.deanp.blog.post.PostView;
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
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 공개 블로그 화면 요청을 받아 Thymeleaf 뷰 모델을 구성한다.
 */
@Controller
@RequiredArgsConstructor
public class BlogController {

    private static final Logger log = LoggerFactory.getLogger(BlogController.class);

    private final PostService postService;
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
     * @param tag 선택된 태그 슬러그, 없으면 태그 조건 생략
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 글 목록 템플릿 이름
     */
    @GetMapping("/posts")
    public String posts(@RequestParam(required = false) String category,
                        @RequestParam(required = false) String series,
                        @RequestParam(required = false, name = "q") String search,
                        @RequestParam(required = false) String tag, Model model) {
        // 선택된 카테고리 조건과 조회 결과를 목록 템플릿 모델에 담는다.
        category = normalize(category);
        series = normalize(series);
        tag = normalize(tag);
        search = normalize(search);
        model.addAttribute("posts", postService.findAll(category, series, tag, search));
        var categories = postService.findCategories();
        var tags = postService.findTags();
        model.addAttribute("categories", categories);
        model.addAttribute("totalPosts", postService.countPublishedPosts());
        model.addAttribute("selectedCategoryName", filterName(categories, category));
        model.addAttribute("selectedTagName", filterName(tags, tag));
        model.addAttribute("seriesOptions", postService.findSeries(category));
        model.addAttribute("selectedSeriesInfo", postService.findSeries(category, series).orElse(null));
        model.addAttribute("tags", tags);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedSeries", series);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("search", search);
        model.addAttribute("hasFilters", category != null || series != null || tag != null || search != null);
        model.addAttribute("pageTitle", "글 — dean.p");

        return "posts";
    }

    /** 선택한 필터는 slug 대신 표시 이름을 사용하며, 알 수 없는 값은 그대로 보존한다. */
    private String filterName(List<com.deanp.blog.post.PostFilterOption> options, String slug) {
        return options.stream().filter(option -> option.slug().equals(slug))
                .map(com.deanp.blog.post.PostFilterOption::name).findFirst().orElse(slug);
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
     * 슬러그로 공개 글 상세 화면을 렌더링한다.
     *
     * @param slug 요청 경로에서 전달된 게시글 슬러그
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param request 방문자 쿠키를 읽을 현재 HTTP 요청
     * @param response 방문자 쿠키를 설정할 현재 HTTP 응답
     * @return 글 상세 템플릿 이름
     */
    @GetMapping("/posts/{slug}")
    public String post(
            @PathVariable String slug,
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

        // 상세 템플릿이 표시할 글과 브라우저 제목을 모델에 담는다.
        model.addAttribute("post", post);
        model.addAttribute("pageTitle", post.title() + " — dean.p");

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
