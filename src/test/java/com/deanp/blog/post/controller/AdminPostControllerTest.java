package com.deanp.blog.post.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.media.service.PostImageService;
import com.deanp.blog.post.service.PostEditorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 글 관리 화면의 로그인·권한·CSRF 경계를 검증한다. */
@WebMvcTest(AdminPostController.class)
@Import(SecurityConfiguration.class)
class AdminPostControllerTest {

    private static final UUID POST_ID = new UUID(0, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostEditorService postEditor;

    @MockitoBean
    private PostImageService postImages;

    /** 빈 목록과 선택지를 돌려주도록 준비한다. */
    @BeforeEach
    void defaults() {
        when(postEditor.findPage(anyInt())).thenReturn(Page.empty());
        when(postEditor.findCategoryChoices()).thenReturn(List.of());
    }

    /** 로그인하지 않은 사용자는 로그인 화면으로 보낸다. */
    @Test
    void redirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/admin/posts"))
                .andExpect(redirectedUrl("/login"));
    }

    /** 로그인했어도 글 관리 권한이 없으면 거부한다. */
    @Test
    void forbidsUserWithoutPostWrite() throws Exception {
        mockMvc.perform(get("/admin/posts").with(user("reader")))
                .andExpect(status().isForbidden());
    }

    /** 글 관리 권한이 있으면 목록을 저장 금지 헤더와 함께 보여 준다. */
    @Test
    void showsListToWriter() throws Exception {
        mockMvc.perform(get("/admin/posts").with(writer()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    /** CSRF 토큰 없는 저장 요청은 권한이 있어도 거부하고 서비스를 호출하지 않는다. */
    @Test
    void rejectsCreateWithoutCsrf() throws Exception {
        mockMvc.perform(post("/admin/posts").with(writer()).param("title", "제목").param("content", "본문"))
                .andExpect(status().isForbidden());

        verify(postEditor, never()).create(any(), any());
    }

    /** CSRF 토큰과 권한이 있으면 로그인 아이디를 작성자로 넘겨 저장하고 수정 화면으로 보낸다. */
    @Test
    void createsPostAsLoggedInWriter() throws Exception {
        when(postEditor.create(any(), eq("admin"))).thenReturn(POST_ID);

        mockMvc.perform(post("/admin/posts").with(writer()).with(csrf())
                        .param("title", "제목").param("content", "본문").param("action", "publish"))
                .andExpect(redirectedUrl("/admin/posts/" + POST_ID + "/edit?saved"));

        verify(postEditor).create(any(), eq("admin"));
    }

    /** 이미지 업로드도 CSRF 토큰이 없으면 거부한다. */
    @Test
    void rejectsImageUploadWithoutCsrf() throws Exception {
        mockMvc.perform(multipart("/admin/posts/{id}/images", POST_ID)
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}))
                        .with(writer()))
                .andExpect(status().isForbidden());

        verify(postImages, never()).upload(any(), any());
    }

    /** @return 글 관리 권한을 가진 로그인 사용자 */
    private static RequestPostProcessor writer() {
        return user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE));
    }
}
