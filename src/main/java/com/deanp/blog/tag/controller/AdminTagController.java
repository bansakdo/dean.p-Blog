package com.deanp.blog.tag.controller;

import com.deanp.blog.tag.service.TagAdminService;
import com.deanp.blog.tag.service.TagAdminService.TagRow;
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

import java.util.UUID;

/**
 * 태그 관리 화면의 목록, 이름 변경, 병합, 삭제 요청을 받는다.
 */
@Controller
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagAdminService tagAdmin;

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
        return "redirect:/admin/tags?conflict";
    }

    /**
     * 모든 태그를 이름순으로 보여 준다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 태그 목록 템플릿 이름
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("tagRows", tagAdmin.findAll());
        model.addAttribute("pageTitle", "태그 관리 — dean.p");
        return "admin/tags";
    }

    /**
     * 태그 하나의 이름 변경, 병합, 삭제 화면을 보여 준다.
     *
     * @param id 태그 식별자
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 태그 관리 템플릿 이름
     */
    @GetMapping("/{id}")
    public String edit(@PathVariable UUID id, Model model) {
        TagRow tag = tagAdmin.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("tagRow", tag);
        model.addAttribute("mergeTargets", tagAdmin.findAll().stream().filter(other -> !other.id().equals(id)).toList());
        model.addAttribute("pageTitle", "태그 " + tag.name() + " — dean.p");
        return "admin/tag-form";
    }

    /**
     * 태그 이름과 주소를 바꾼다.
     *
     * @param id 태그 식별자
     * @param name 새 이름
     * @param slug 새 주소, 비우면 이름으로 만든다
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 태그 화면으로의 리다이렉트
     */
    @PostMapping("/{id}")
    public String rename(@PathVariable UUID id, @RequestParam String name, @RequestParam(defaultValue = "") String slug,
                         RedirectAttributes redirect) {
        try {
            tagAdmin.rename(id, name, slug);
            return "redirect:/admin/tags/" + id + "?saved";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/tags/" + id;
        }
    }

    /**
     * 이 태그를 다른 태그로 합치고 목록으로 돌아간다.
     *
     * @param id 없앨 태그 식별자
     * @param targetId 남길 태그 식별자
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 목록 또는 태그 화면으로의 리다이렉트
     */
    @PostMapping("/{id}/merge")
    public String merge(@PathVariable UUID id, @RequestParam UUID targetId, RedirectAttributes redirect) {
        try {
            tagAdmin.merge(id, targetId);
            return "redirect:/admin/tags?merged";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/tags/" + id;
        }
    }

    /**
     * 태그를 지우고 목록으로 돌아간다.
     *
     * @param id 태그 식별자
     * @return 목록으로의 리다이렉트
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        try {
            tagAdmin.delete(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/admin/tags?deleted";
    }
}
