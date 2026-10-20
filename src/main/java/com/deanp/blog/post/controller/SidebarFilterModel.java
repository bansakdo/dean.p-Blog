package com.deanp.blog.post.controller;

import com.deanp.blog.post.PostFilterOption;
import com.deanp.blog.post.service.BoardService;
import com.deanp.blog.post.service.BoardService.BoardView;
import com.deanp.blog.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 사이드 메뉴의 게시판(개발·노트) 하위 메뉴와 상세 조회 조건이 쓰는 선택지와 선택 상태를 화면 모델에 담는다.
 * 모든 게시판의 카테고리 하위 메뉴는 어느 화면에서든 펼쳐 볼 수 있고({@link SidebarFilterInterceptor}가 채움),
 * 시리즈·태그 같은 상세 조회 조건은 게시판 목록·글 상세에서 그 게시판 아래에만 붙는다.
 */
@Component
@RequiredArgsConstructor
public class SidebarFilterModel {

    /** 처음 보여 주고, 더 보기 때마다 더 불러오는 태그 수 */
    static final int TAG_BATCH_SIZE = 20;
    /** 필터 링크가 기본으로 유지하는 페이지당 글 수 */
    static final int DEFAULT_PAGE_SIZE = 20;

    private final PostService postService;
    private final BoardService boardService;

    /**
     * 필터 선택지와 선택 상태를 모델에 담는다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param board 필터를 보여 줄 게시판
     * @param category 선택된 카테고리 슬러그 또는 null
     * @param series 선택된 시리즈 슬러그 또는 null
     * @param selectedTags 선택된 태그 슬러그 목록
     * @param search 검색어 또는 null
     * @param size 필터 링크가 유지할 페이지당 글 수
     */
    public void add(Map<String, Object> model, BoardView board, String category, String series,
                    List<String> selectedTags, String search, int size) {
        // 모든 게시판의 하위 메뉴와, 상세 조회 조건을 붙일 지금 게시판을 담는다.
        List<BoardMenu> menus = boardMenus();
        model.put("boardMenus", menus);
        model.put("filterBoard", board);
        model.put("showBoardFilters", true);

        // 지금 게시판의 카테고리와 시리즈 선택지를 현재 선택 상태와 함께 담는다.
        var categories = menus.stream().filter(menu -> menu.board().id().equals(board.id()))
                .map(BoardMenu::categories).findFirst().orElseGet(() -> postService.findCategories(board.slug()));
        model.put("categories", categories);
        model.put("totalPosts", postService.countPublishedPosts(board.slug()));
        model.put("selectedCategoryName", filterName(categories, category));
        model.put("seriesOptions", postService.findSeries(board.slug(), category));

        // 첫 태그 구간과, 그 구간 밖에 있는 선택 태그를 칩으로 만든다.
        var tags = postService.findTags(board.slug(), 0, TAG_BATCH_SIZE + 1);
        var allTags = selectedTags.isEmpty() ? List.<PostFilterOption>of() : postService.findTags(board.slug());
        var firstTags = tags.stream().limit(TAG_BATCH_SIZE).toList();
        var selectedChips = selectedTags.stream()
                .map(slug -> tagChip(new PostFilterOption(slug, filterName(allTags, slug)), selectedTags)).toList();
        model.put("selectedTagFilters", selectedChips);
        model.put("pinnedTagChips", selectedChips.stream()
                .filter(chip -> firstTags.stream().noneMatch(option -> option.slug().equals(chip.slug()))).toList());
        model.put("tags", firstTags);
        model.put("tagChips", firstTags.stream()
                .map(option -> tagChip(option, selectedTags)).toList());
        model.put("hasMoreTags", tags.size() > TAG_BATCH_SIZE);

        // 필터 링크가 현재 조건을 이어 가도록 선택 값을 담는다.
        model.put("selectedCategory", category);
        model.put("selectedSeries", series);
        model.put("selectedTag", selectedTags.isEmpty() ? null : String.join(",", selectedTags));
        model.put("search", search);
        model.put("pageSize", size);
    }

    /**
     * 기본 게시판(정렬 순서가 가장 앞선 활성 게시판)이다. 예전 /posts 주소와, 게시판을 모르는 글 상세가 쓴다.
     *
     * @return 기본 게시판
     */
    public BoardView defaultBoard() {
        return boardService.findActive().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("활성 게시판이 없습니다."));
    }

    /**
     * 게시판 목록·글 상세가 아닌 화면에 모든 게시판의 카테고리 하위 메뉴만 담는다. 상세 조회 조건은 담지 않는다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     */
    public void addMenus(Map<String, Object> model) {
        model.put("boardMenus", boardMenus());
    }

    /**
     * @return 활성 게시판마다 공개 글 수와 공개 글이 있는 카테고리를 담은 하위 메뉴
     */
    private List<BoardMenu> boardMenus() {
        return boardService.findActive().stream()
                .map(board -> new BoardMenu(board, postService.countPublishedPosts(board.slug()),
                        postService.findCategories(board.slug())))
                .toList();
    }

    /**
     * 사이드 메뉴의 게시판 하나와 그 하위 메뉴다.
     *
     * @param board 게시판
     * @param totalPosts 게시판의 공개 글 수
     * @param categories 공개 글이 있는 카테고리와 글 수
     */
    public record BoardMenu(BoardView board, long totalPosts, List<PostFilterOption> categories) { }

    /** @param option 화면에 표시할 태그 @param selected 선택된 태그 목록 @return 클릭 시 해당 태그만 전환하는 링크 데이터 */
    private TagChip tagChip(PostFilterOption option, List<String> selected) {
        var next = new ArrayList<>(selected);
        boolean active = next.remove(option.slug());
        if (!active) next.add(option.slug());
        return new TagChip(option.slug(), option.name(), active, next.isEmpty() ? null : String.join(",", next));
    }

    /** 선택한 필터는 slug 대신 표시 이름을 사용하며, 알 수 없는 값은 그대로 보존한다. */
    private String filterName(List<PostFilterOption> options, String slug) {
        return options.stream().filter(option -> option.slug().equals(slug))
                .map(PostFilterOption::name).findFirst().orElse(slug);
    }

    /** @param slug 태그 슬러그 @param name 표시 이름 @param selected 현재 선택 여부 @param nextTags 클릭 후 남는 태그 슬러그 문자열 */
    public record TagChip(String slug, String name, boolean selected, String nextTags) { }
}
