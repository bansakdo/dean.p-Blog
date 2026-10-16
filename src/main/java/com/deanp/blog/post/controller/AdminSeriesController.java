package com.deanp.blog.post.controller;

import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.post.service.PostSeriesCommand;
import com.deanp.blog.post.service.PostSeriesService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * 시리즈 관리 화면의 목록, 작성, 수정, 삭제, 글 배치 요청을 받는다.
 */
@Controller
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class AdminSeriesController {

    private final PostSeriesService seriesService;
    private final PostEditorService postEditor;

    /**
     * 관리 화면 응답은 브라우저 캐시에 남기지 않는다.
     *
     * @param response 현재 HTTP 응답
     */
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    /**
     * 확인과 저장 사이에 다른 요청이 같은 이름·주소·순서를 쓰거나 분류를 바꿔 DB 제약에 걸리면 목록에서 안내한다.
     *
     * @return 충돌 안내를 띄울 목록으로의 리다이렉트
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String conflict() {
        return "redirect:/admin/series?conflict";
    }

    /**
     * 모든 시리즈를 정렬 순서대로 보여 준다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 시리즈 목록 템플릿 이름
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("seriesRows", seriesService.findAll());
        model.addAttribute("pageTitle", "시리즈 관리 — dean.p");
        return "admin/series";
    }

    /**
     * 빈 시리즈 작성 폼을 보여 준다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 시리즈 작성·수정 템플릿 이름
     */
    @GetMapping("/new")
    public String newSeries(Model model) {
        model.addAttribute("form", new SeriesForm());
        return form(model, null);
    }

    /**
     * 새 시리즈를 만들고 수정 화면으로 이동한다.
     *
     * @param form 작성 폼 입력 값
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 수정 화면으로의 리다이렉트 또는 오류가 표시된 작성 폼
     */
    @PostMapping
    public String create(@ModelAttribute("form") SeriesForm form, Model model) {
        try {
            UUID id = seriesService.createSeries(command(form));
            return "redirect:/admin/series/" + id + "/edit?saved";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return form(model, null);
        }
    }

    /**
     * 시리즈의 현재 값과 속한 글을 보여 준다.
     *
     * @param id 시리즈 식별자
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 시리즈 작성·수정 템플릿 이름
     */
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        PostSeriesCommand current = seriesService.findForEdit(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SeriesForm form = new SeriesForm();
        form.setCategoryId(current.categoryId());
        form.setName(current.name());
        form.setSlug(current.slug());
        form.setDescription(current.description());
        form.setSortOrder(current.sortOrder());
        form.setStatus(current.status());
        model.addAttribute("form", form);
        return form(model, id);
    }

    /**
     * 시리즈를 고친다. 대표 카테고리를 바꾸면 속한 글의 카테고리도 함께 바뀐다.
     *
     * @param id 시리즈 식별자
     * @param form 수정 폼 입력 값
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 수정 화면으로의 리다이렉트 또는 오류가 표시된 수정 폼
     */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute("form") SeriesForm form, Model model) {
        requireSeries(id);
        try {
            seriesService.updateSeries(id, command(form));
            return "redirect:/admin/series/" + id + "/edit?saved";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return form(model, id);
        }
    }

    /**
     * 시리즈를 지우고 목록으로 돌아간다. 속한 글이 있으면 사유를 보여 준다.
     *
     * @param id 시리즈 식별자
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 목록 또는 수정 화면으로의 리다이렉트
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        requireSeries(id);
        try {
            seriesService.deleteSeries(id);
            return "redirect:/admin/series?deleted";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/series/" + id + "/edit";
        }
    }

    /**
     * 글을 시리즈에 넣거나, 이미 속한 글의 순서를 바꾼다. 글의 카테고리는 시리즈 대표 카테고리로 바뀐다.
     *
     * @param id 시리즈 식별자
     * @param postId 게시글 식별자
     * @param seriesOrder 시리즈 안 순서
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 수정 화면으로의 리다이렉트
     */
    @PostMapping("/{id}/posts")
    public String assignPost(@PathVariable UUID id, @RequestParam UUID postId, @RequestParam int seriesOrder,
                             RedirectAttributes redirect) {
        requireSeries(id);
        try {
            seriesService.assignPost(id, postId, seriesOrder);
            return "redirect:/admin/series/" + id + "/edit?postsSaved";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/series/" + id + "/edit";
        }
    }

    /**
     * 글을 시리즈에서 뺀다. 이 시리즈에 속한 글이 아니면 거부한다.
     *
     * @param id 시리즈 식별자
     * @param postId 게시글 식별자
     * @return 수정 화면으로의 리다이렉트
     */
    @PostMapping("/{id}/posts/{postId}/remove")
    public String removePost(@PathVariable UUID id, @PathVariable UUID postId) {
        requireSeries(id);
        if (seriesService.findMembers(id).stream().noneMatch(member -> member.id().equals(postId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        seriesService.removePost(postId);
        return "redirect:/admin/series/" + id + "/edit?postsSaved";
    }

    /**
     * 작성·수정 폼에 필요한 선택지와 속한 글을 모델에 담는다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param id 수정 중인 시리즈 식별자, 새 시리즈면 null
     * @return 시리즈 작성·수정 템플릿 이름
     */
    private String form(Model model, UUID id) {
        var members = id == null ? List.<PostSeriesService.SeriesMember>of() : seriesService.findMembers(id);
        model.addAttribute("seriesId", id);
        model.addAttribute("categoryChoices", postEditor.findCategoryChoices());
        model.addAttribute("members", members);
        model.addAttribute("candidates", id == null ? List.of() : seriesService.findCandidates());
        model.addAttribute("nextOrder", members.stream().mapToInt(member -> member.seriesOrder()).max().orElse(0) + 1);
        model.addAttribute("pageTitle", (id == null ? "새 시리즈" : "시리즈 수정") + " — dean.p");
        return "admin/series-form";
    }

    /**
     * @param id 시리즈 식별자
     * @return 없으면 404
     */
    private PostSeriesCommand requireSeries(UUID id) {
        return seriesService.findForEdit(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * @param form 폼 입력 값
     * @return 시리즈 생성·수정 명령
     */
    private static PostSeriesCommand command(SeriesForm form) {
        return new PostSeriesCommand(form.getCategoryId(), form.getSlug(), form.getName(), form.getDescription(),
                form.getSortOrder(), form.getStatus());
    }
}
