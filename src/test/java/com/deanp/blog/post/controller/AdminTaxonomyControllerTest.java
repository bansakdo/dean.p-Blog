package com.deanp.blog.post.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.post.service.CategoryAdminService;
import com.deanp.blog.post.service.CategoryAdminService.CategoryRow;
import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.post.service.PostSeriesCommand;
import com.deanp.blog.post.service.PostSeriesService;
import com.deanp.blog.post.service.PostSeriesService.SeriesMember;
import com.deanp.blog.tag.controller.AdminTagController;
import com.deanp.blog.tag.service.TagAdminService;
import com.deanp.blog.tag.service.TagAdminService.TagRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 카테고리·시리즈·태그 관리 화면의 권한, 요청 전달, 오류 안내를 검증한다. */
@WebMvcTest({AdminCategoryController.class, AdminSeriesController.class, AdminTagController.class})
@Import(SecurityConfiguration.class)
class AdminTaxonomyControllerTest {

    private static final UUID ID = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryAdminService categoryAdmin;
    @MockitoBean
    private PostSeriesService seriesService;
    @MockitoBean
    private PostEditorService postEditor;
    @MockitoBean
    private TagAdminService tagAdmin;

    /** 글 관리 권한이 없으면 관리 화면을 열거나 바꿀 수 없다. */
    @Test
    void requiresPostWrite() throws Exception {
        for (String path : List.of("/admin/categories", "/admin/series", "/admin/tags")) {
            mockMvc.perform(get(path)).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get(path).with(user("reader"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/admin/categories/{id}/delete", ID).with(writer()))
                .andExpect(status().isForbidden());
        verify(categoryAdmin, never()).delete(any());
    }

    /** 카테고리 목록은 관리 메뉴와 줄마다 수정·삭제 폼을 보여 주고, 삭제 거부 사유를 안내한다. */
    @Test
    void managesCategories() throws Exception {
        when(categoryAdmin.findAll()).thenReturn(List.of(new CategoryRow(ID, "개발", "dev", 1, 3, 1)));
        mockMvc.perform(get("/admin/categories").with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("aria-current=\"page\">카테고리"),
                        containsString("value=\"dev\""), containsString("글 3편 · 시리즈 1개"),
                        containsString("/admin/categories/" + ID + "/delete"))));

        doThrow(new IllegalArgumentException("글 3편과 시리즈 1개가 이 카테고리를 쓰고 있어 삭제할 수 없습니다.")).when(categoryAdmin).delete(ID);
        mockMvc.perform(post("/admin/categories/{id}/delete", ID).with(writer()).with(csrf()))
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("error", containsString("삭제할 수 없습니다")));

        mockMvc.perform(post("/admin/categories").with(writer()).with(csrf())
                        .param("name", "일상").param("slug", "").param("sortOrder", "2"))
                .andExpect(redirectedUrl("/admin/categories?saved"));
        verify(categoryAdmin).create(new CategoryAdminService.CategoryInput("일상", "", 2));
    }

    /** 시리즈 수정 화면은 속한 글과 넣을 글을 보여 주고, 글 배치와 다른 시리즈 글 빼기 거부를 처리한다. */
    @Test
    void managesSeriesPosts() throws Exception {
        when(seriesService.findForEdit(ID)).thenReturn(Optional.of(new PostSeriesCommand(OTHER, "boot", "부트", null, 0, "HIDDEN")));
        when(seriesService.findMembers(ID)).thenReturn(List.of(new SeriesMember(OTHER, "첫 글", "PUBLISHED", 1, true)));
        when(seriesService.findCandidates()).thenReturn(List.of(new SeriesMember(ID, "넣을 글", "DRAFT", null, false)));
        when(postEditor.findCategoryChoices()).thenReturn(List.of(new PostEditorService.CategoryChoice(OTHER, "개발")));

        mockMvc.perform(get("/admin/series/{id}/edit", ID).with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("첫 글"), containsString("admin-badge is-scheduled"), containsString("넣을 글 (초안)"),
                        containsString("value=\"2\""), containsString("<option value=\"HIDDEN\" selected=\"selected\">"))));

        mockMvc.perform(post("/admin/series/{id}/posts", ID).with(writer()).with(csrf())
                        .param("postId", ID.toString()).param("seriesOrder", "2"))
                .andExpect(redirectedUrl("/admin/series/" + ID + "/edit?postsSaved"));
        verify(seriesService).assignPost(ID, ID, 2);

        mockMvc.perform(post("/admin/series/{id}/posts/{postId}/remove", ID, ID).with(writer()).with(csrf()))
                .andExpect(status().isNotFound());
        verify(seriesService, never()).removePost(ID);

        doThrow(new org.springframework.dao.DataIntegrityViolationException("ux_post_detail_series_order"))
                .when(seriesService).assignPost(ID, OTHER, 1);
        mockMvc.perform(post("/admin/series/{id}/posts", ID).with(writer()).with(csrf())
                        .param("postId", OTHER.toString()).param("seriesOrder", "1"))
                .andExpect(redirectedUrl("/admin/series?conflict"));
    }

    /** 시리즈 작성 실패는 입력 값을 유지한 폼에 사유를 보여 준다. */
    @Test
    void rendersSeriesErrorWithInput() throws Exception {
        when(seriesService.createSeries(any())).thenThrow(new IllegalArgumentException("시리즈 대표 카테고리는 필수입니다."));

        mockMvc.perform(post("/admin/series").with(writer()).with(csrf()).param("name", "새 시리즈").param("description", "소개 글"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("시리즈 대표 카테고리는 필수입니다."),
                        containsString("value=\"새 시리즈\""), containsString("소개 글"))));
    }

    /** 태그 화면은 자기 자신을 뺀 병합 대상을 보여 주고, 병합·삭제 후 목록으로 보낸다. */
    @Test
    void managesTags() throws Exception {
        when(tagAdmin.find(ID)).thenReturn(Optional.of(new TagRow(ID, "spring", "spring", 2)));
        when(tagAdmin.findAll()).thenReturn(List.of(new TagRow(ID, "spring", "spring", 2), new TagRow(OTHER, "Spring Boot", "spring-boot", 1)));

        mockMvc.perform(get("/admin/tags/{id}", ID).with(writer()))
                .andExpect(content().string(allOf(containsString("Spring Boot (1편)"), not(containsString("spring (2편)")))));

        mockMvc.perform(post("/admin/tags/{id}/merge", ID).with(writer()).with(csrf()).param("targetId", OTHER.toString()))
                .andExpect(redirectedUrl("/admin/tags?merged"));
        verify(tagAdmin).merge(ID, OTHER);

        mockMvc.perform(post("/admin/tags/{id}/delete", ID).with(writer()).with(csrf()))
                .andExpect(redirectedUrl("/admin/tags?deleted"));

        doThrow(new IllegalArgumentException("이미 있는 태그 이름입니다.")).when(tagAdmin).rename(eq(ID), any(), any());
        mockMvc.perform(post("/admin/tags/{id}", ID).with(writer()).with(csrf()).param("name", "Spring Boot"))
                .andExpect(redirectedUrl("/admin/tags/" + ID))
                .andExpect(flash().attribute("error", "이미 있는 태그 이름입니다."));
    }

    /** @return 글 관리 권한을 가진 로그인 사용자 */
    private static RequestPostProcessor writer() {
        return user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE));
    }
}
