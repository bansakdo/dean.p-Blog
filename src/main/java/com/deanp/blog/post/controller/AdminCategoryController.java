package com.deanp.blog.post.controller;

import com.deanp.blog.post.service.CategoryAdminService;
import com.deanp.blog.post.service.CategoryAdminService.CategoryInput;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * 카테고리 관리 화면의 목록, 생성, 수정, 삭제 요청을 받는다.
 */
@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private static final String LIST = "redirect:/admin/categories";

    private final CategoryAdminService categoryAdmin;

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
        return "redirect:/admin/categories?conflict";
    }

    /**
     * 카테고리를 정렬 순서대로 보여 주고, 각 줄에서 바로 고치거나 지울 수 있게 한다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 카테고리 관리 템플릿 이름
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("categoryRows", categoryAdmin.findAll());
        model.addAttribute("pageTitle", "카테고리 관리 — dean.p");
        return "admin/categories";
    }

    /**
     * 새 카테고리를 만든다. 실패하면 입력 값과 사유를 들고 목록으로 돌아간다.
     *
     * @param name 이름
     * @param slug 주소, 비우면 이름으로 만든다
     * @param sortOrder 정렬 순서
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 목록으로의 리다이렉트
     */
    @PostMapping
    public String create(@RequestParam String name, @RequestParam(defaultValue = "") String slug,
                         @RequestParam(defaultValue = "0") int sortOrder, RedirectAttributes redirect) {
        try {
            categoryAdmin.create(new CategoryInput(name, slug, sortOrder));
            return LIST + "?saved";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            redirect.addFlashAttribute("draft", new CategoryInput(name, slug, sortOrder));
            return LIST;
        }
    }

    /**
     * 카테고리의 이름, 주소, 정렬 순서를 고친다.
     *
     * @param id 카테고리 식별자
     * @param name 이름
     * @param slug 주소, 비우면 이름으로 만든다
     * @param sortOrder 정렬 순서
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 목록으로의 리다이렉트
     */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @RequestParam String name, @RequestParam(defaultValue = "") String slug,
                         @RequestParam(defaultValue = "0") int sortOrder, RedirectAttributes redirect) {
        try {
            categoryAdmin.update(id, new CategoryInput(name, slug, sortOrder));
            return LIST + "?saved";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return LIST;
        }
    }

    /**
     * 카테고리를 지운다. 쓰고 있는 글이나 시리즈가 있으면 사유를 보여 준다.
     *
     * @param id 카테고리 식별자
     * @param redirect 리다이렉트 후 보여 줄 값
     * @return 목록으로의 리다이렉트
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            categoryAdmin.delete(id);
            return LIST + "?deleted";
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return LIST;
        }
    }
}
