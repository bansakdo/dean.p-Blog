package com.deanp.blog.post.controller;

import com.deanp.blog.media.service.MediaImageValidationService.MediaValidationException;
import com.deanp.blog.media.service.PostImageService;
import com.deanp.blog.media.service.PostImageService.PostImage;
import com.deanp.blog.post.service.AdminPostFilter;
import com.deanp.blog.post.service.PostEditCommand;
import com.deanp.blog.post.service.PostEditCommand.PublishAction;
import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.post.service.PostEditorService.PostEditView;
import com.deanp.blog.post.service.PostEditorService.SlugConflictException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 글 관리 화면의 목록, 작성, 수정, 삭제·복구, 이미지 업로드, 미리보기 요청을 받는다.
 */
@Controller
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostEditorService postEditor;
    private final PostImageService postImages;

    /**
     * 초안과 로그인 사용자 정보가 브라우저 캐시에 남지 않도록 관리 화면 응답은 저장하지 않게 한다.
     *
     * @param response 현재 HTTP 응답
     */
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    /**
     * 초안을 포함한 글 목록을 상태와 제목 검색어로 걸러 최근 수정순으로 보여 준다.
     *
     * @param page 1부터 시작하는 페이지 번호
     * @param status 상태 필터(빈 값, draft, published, deleted)
     * @param q 제목 검색어
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 글 관리 목록 템플릿 이름
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "") String q, Model model) {
        AdminPostFilter filter = AdminPostFilter.fromParam(status)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String query = q.strip();
        var postPage = postEditor.findPage(page - 1, filter, query);
        model.addAttribute("status", filter.getParam());
        model.addAttribute("q", query);
        model.addAttribute("filtered", filter != AdminPostFilter.ALL || !query.isEmpty());
        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("totalPosts", postPage.getTotalElements());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", Math.max(1, postPage.getTotalPages()));
        model.addAttribute("pageTitle", "글 관리 — dean.p");
        return "admin/posts";
    }

    /**
     * 빈 작성 폼을 보여 준다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 글 작성·수정 템플릿 이름
     */
    @GetMapping("/new")
    public String newPost(Model model) {
        model.addAttribute("form", new PostForm());
        return form(model, null);
    }

    /**
     * 새 글을 저장하고 수정 화면으로 이동한다.
     *
     * @param form 작성 폼 입력 값
     * @param bindingResult 입력 검증 결과
     * @param action 저장 후 공개 상태(save, publish)
     * @param principal 로그인한 작성자
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 수정 화면으로의 리다이렉트 또는 오류가 표시된 작성 폼
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("form") PostForm form, BindingResult bindingResult,
                         @RequestParam(defaultValue = "save") String action, Principal principal, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                UUID id = postEditor.create(command(form, action), principal.getName());
                return "redirect:/admin/posts/" + id + "/edit?saved";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("post", exception.getMessage());
            }
        }
        return form(model, null);
    }

    /**
     * 기존 글의 현재 값을 채운 수정 폼을 보여 준다.
     *
     * @param id 게시글 식별자
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 글 작성·수정 템플릿 이름
     */
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        PostEditView post = requirePost(id);
        PostForm form = new PostForm();
        form.setTitle(post.title());
        form.setSlug(post.slug());
        form.setSummary(post.summary());
        form.setContent(post.content());
        form.setCategoryId(post.categoryId());
        form.setTags(post.tags());
        form.setRepresentativeImageId(post.representativeImageId());
        model.addAttribute("form", form);
        return form(model, post);
    }

    /**
     * 기존 글을 저장하고 수정 화면으로 돌아간다.
     *
     * @param id 게시글 식별자
     * @param form 수정 폼 입력 값
     * @param bindingResult 입력 검증 결과
     * @param action 저장 후 공개 상태(save, publish, unpublish)
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 수정 화면으로의 리다이렉트 또는 오류가 표시된 수정 폼
     */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("form") PostForm form,
                         BindingResult bindingResult, @RequestParam(defaultValue = "save") String action,
                         Model model) {
        PostEditView post = requirePost(id);
        if (!bindingResult.hasErrors()) {
            try {
                postEditor.update(id, command(form, action));
                return "redirect:/admin/posts/" + id + "/edit?saved";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("post", exception.getMessage());
            }
        }
        return form(model, post);
    }

    /**
     * 글을 삭제 상태로 바꾸고 목록으로 돌아간다.
     *
     * @param id 게시글 식별자
     * @return 삭제 안내를 띄울 목록으로의 리다이렉트
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        requirePost(id);
        postEditor.delete(id);
        return "redirect:/admin/posts?deleted";
    }

    /**
     * 삭제한 글을 초안으로 되돌리고 수정 화면으로 이동한다. 삭제 상태가 아니면 거부하고,
     * 다른 글이 같은 주소를 쓰고 있으면 복구하지 않고 안내를 띄운다.
     *
     * @param id 게시글 식별자
     * @return 수정 화면으로의 리다이렉트
     */
    @PostMapping("/{id}/restore")
    public String restore(@PathVariable UUID id) {
        requirePost(id);
        try {
            // 다른 요청이 먼저 복구했을 수 있으므로 잠근 행의 상태로 서비스가 판단한다.
            postEditor.restore(id);
        } catch (SlugConflictException exception) {
            return "redirect:/admin/posts/" + id + "/edit?slugConflict";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return "redirect:/admin/posts/" + id + "/edit?restored";
    }

    /**
     * 글에 이미지를 올리고 본문에 넣을 주소를 응답한다. 삭제한 글에는 올릴 수 없다.
     *
     * @param id 게시글 식별자
     * @param file 업로드한 이미지 파일
     * @return 첨부된 이미지의 식별자, 파일명, 공개 경로
     */
    @PostMapping("/{id}/images")
    @ResponseBody
    public PostImage uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        if (requirePost(id).deleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return postImages.upload(id, file);
    }

    /**
     * 저장된 글을 공개 여부와 관계없이 공개 상세 화면 모양으로 보여 준다.
     *
     * @param id 게시글 식별자
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @return 글 상세 템플릿 이름
     */
    @GetMapping("/{id}/preview")
    public String preview(@PathVariable UUID id, Model model) {
        var post = postEditor.findPreview(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        model.addAttribute("pageTitle", "미리보기: " + post.title() + " — dean.p");
        return "post";
    }

    /**
     * 이미지 검증 실패를 업로드 스크립트가 읽을 수 있는 메시지로 응답한다.
     *
     * @param exception 이미지 검증 실패
     * @return 400 응답과 실패 사유
     */
    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<Map<String, String>> invalidImage(MediaValidationException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    /**
     * 작성·수정 폼에 공통으로 필요한 선택지와 글 상태를 모델에 담는다.
     *
     * @param model Thymeleaf 렌더링에 사용할 모델
     * @param post 수정 중인 글의 저장된 값, 새 글이면 null
     * @return 글 작성·수정 템플릿 이름
     */
    private String form(Model model, PostEditView post) {
        model.addAttribute("categoryChoices", postEditor.findCategoryChoices());
        model.addAttribute("post", post);
        model.addAttribute("images", post == null ? List.of() : postImages.findByPost(post.id()));
        model.addAttribute("pageTitle", (post == null ? "새 글" : "글 수정") + " — dean.p");
        return "admin/post-form";
    }

    /**
     * @param id 게시글 식별자
     * @return 저장된 글의 현재 값, 없으면 404
     */
    private PostEditView requirePost(UUID id) {
        return postEditor.findForEdit(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * 폼 입력과 누른 버튼을 서비스 명령으로 바꾼다.
     *
     * @param form 폼 입력 값
     * @param action 누른 버튼 값
     * @return 글 작성·수정 명령
     */
    private PostEditCommand command(PostForm form, String action) {
        PublishAction publishAction = switch (action) {
            case "save" -> PublishAction.KEEP;
            case "publish" -> PublishAction.PUBLISH;
            case "unpublish" -> PublishAction.UNPUBLISH;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        List<String> tagNames = form.getTags() == null ? List.of() : Arrays.asList(form.getTags().split(","));
        return new PostEditCommand(form.getTitle(), form.getSlug(), form.getSummary(), form.getContent(),
                form.getCategoryId(), tagNames, form.getRepresentativeImageId(), publishAction);
    }
}
