package com.deanp.blog.post.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.auth.service.BlogUserDetailsService;
import com.deanp.blog.media.service.MediaImageValidationService.MediaValidationException;
import com.deanp.blog.media.service.PostDiagramService;
import com.deanp.blog.media.service.PostDiagramService.PostDiagram;
import com.deanp.blog.media.service.PostFileService;
import com.deanp.blog.media.service.PostFileService.PostFile;
import com.deanp.blog.media.service.PostImageService;
import com.deanp.blog.post.service.AdminPostFilter;
import com.deanp.blog.post.service.PostEditorService;
import com.deanp.blog.post.service.PostEditorService.PostEditView;
import com.deanp.blog.post.service.PostService;
import com.deanp.blog.publishing.service.PostRevisionService;
import com.deanp.blog.publishing.service.PostRevisionService.RevisionRow;
import com.deanp.blog.publishing.service.PostRevisionService.RevisionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @MockitoBean
    private PostRevisionService revisions;

    @MockitoBean
    private PostDiagramService postDiagrams;

    @MockitoBean
    private PostFileService postFiles;

    @MockitoBean
    private PostService postService;

    /** 빈 목록과 선택지를 돌려주도록 준비한다. */
    @BeforeEach
    void defaults() {
        when(postEditor.findPage(anyInt(), any(), any())).thenReturn(Page.empty());
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

    /** 다이어그램 업로드는 본문 블록을 돌려주고, CSRF가 없으면 거부하며, 검증 실패는 메시지와 400으로 답한다. */
    @Test
    void uploadsDiagramAndReportsValidationErrors() throws Exception {
        UUID diagramId = new UUID(0, 9);
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));
        when(postDiagrams.upload(eq(POST_ID), any())).thenReturn(new PostDiagram(diagramId, "flow.html"));
        MockMultipartFile file = new MockMultipartFile("file", "flow.html", "text/html", new byte[] {1});

        mockMvc.perform(multipart("/admin/posts/{id}/diagrams", POST_ID).file(file).with(writer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/posts/{id}/diagrams", POST_ID).file(file).with(writer()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(diagramId.toString()))
                .andExpect(jsonPath("$.markdown").value("```archify\n" + diagramId + "\n```"));

        when(postDiagrams.upload(eq(POST_ID), any())).thenThrow(new MediaValidationException("Archify로 만든 HTML이 아닙니다."));
        mockMvc.perform(multipart("/admin/posts/{id}/diagrams", POST_ID).file(file).with(writer()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Archify로 만든 HTML이 아닙니다."));
    }

    /** 첨부 파일 업로드는 본문 링크와 크기를 돌려주고, CSRF가 없으면 거부하며, 삭제한 글에는 올릴 수 없다. */
    @Test
    void uploadsFileAndRejectsDeletedPost() throws Exception {
        UUID fileId = new UUID(0, 11);
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));
        when(postFiles.upload(eq(POST_ID), any())).thenReturn(new PostFile(fileId, "slides.pdf", 2048));
        MockMultipartFile file = new MockMultipartFile("file", "slides.pdf", "application/pdf", new byte[] {1});

        mockMvc.perform(multipart("/admin/posts/{id}/files", POST_ID).file(file).with(writer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/posts/{id}/files", POST_ID).file(file).with(writer()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.size").value("2 KB"))
                .andExpect(jsonPath("$.url").value("/media/files/" + fileId))
                .andExpect(jsonPath("$.markdown").value("[slides.pdf](/media/files/" + fileId + ")"));

        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));
        mockMvc.perform(multipart("/admin/posts/{id}/files", POST_ID).file(file).with(writer()).with(csrf()))
                .andExpect(status().isConflict());
        verify(postFiles).upload(eq(POST_ID), any());
    }

    /** 첨부 파일 삭제는 이 글의 첨부 파일이면 204, 아니면 404, 삭제한 글이면 409로 답한다. */
    @Test
    void deletesFileOfThisPostOnly() throws Exception {
        UUID fileId = new UUID(0, 11);
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));
        when(postFiles.delete(POST_ID, fileId)).thenReturn(true);

        mockMvc.perform(post("/admin/posts/{id}/files/{fileId}/delete", POST_ID, fileId).with(writer()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/admin/posts/{id}/files/{fileId}/delete", POST_ID, new UUID(0, 12)).with(writer()).with(csrf()))
                .andExpect(status().isNotFound());

        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));
        mockMvc.perform(post("/admin/posts/{id}/files/{fileId}/delete", POST_ID, fileId).with(writer()).with(csrf()))
                .andExpect(status().isConflict());
    }

    /** 실시간 미리보기는 글쓴이만 쓰며, 공개 화면과 같은 변환 결과를 돌려준다. 관리 화면은 iframe 삽입을 막는다. */
    @Test
    void rendersPreviewForWritersOnly() throws Exception {
        when(postService.renderHtml("# 제목")).thenReturn("<h1>제목</h1>");

        mockMvc.perform(post("/admin/posts/preview-render").param("content", "# 제목").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/posts/preview-render").param("content", "# 제목").with(writer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/posts/preview-render").param("content", "# 제목").with(writer()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("<h1>제목</h1>"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    /** 상태 필터와 앞뒤 공백을 지운 제목 검색어를 서비스에 넘긴다. */
    @Test
    void passesFilterAndQueryToService() throws Exception {
        mockMvc.perform(get("/admin/posts").with(writer()).param("status", "deleted").param("q", "  스프링 "))
                .andExpect(status().isOk());

        verify(postEditor).findPage(0, AdminPostFilter.DELETED, "스프링");
    }

    /** 모르는 상태 필터는 거부한다. */
    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/admin/posts").with(writer()).param("status", "DELETED"))
                .andExpect(status().isBadRequest());
    }

    /** 삭제 요청도 CSRF 토큰이 없으면 거부한다. */
    @Test
    void rejectsDeleteWithoutCsrf() throws Exception {
        mockMvc.perform(post("/admin/posts/{id}/delete", POST_ID).with(writer()))
                .andExpect(status().isForbidden());

        verify(postEditor, never()).delete(any(), any());
    }

    /** 글 관리 권한이 없으면 삭제할 수 없다. */
    @Test
    void forbidsDeleteWithoutPostWrite() throws Exception {
        mockMvc.perform(post("/admin/posts/{id}/delete", POST_ID).with(user("reader")).with(csrf()))
                .andExpect(status().isForbidden());

        verify(postEditor, never()).delete(any(), any());
    }

    /** 삭제하면 안내를 띄울 목록으로 보낸다. */
    @Test
    void deletesPost() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));

        mockMvc.perform(post("/admin/posts/{id}/delete", POST_ID).with(writer()).with(csrf()))
                .andExpect(redirectedUrl("/admin/posts?deleted"));

        verify(postEditor).delete(eq(POST_ID), any());
    }

    /** 없는 글은 삭제하지 않고 404로 응답한다. */
    @Test
    void returnsNotFoundForMissingPost() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/posts/{id}/delete", POST_ID).with(writer()).with(csrf()))
                .andExpect(status().isNotFound());

        verify(postEditor, never()).delete(any(), any());
    }

    /** 삭제한 글은 복구해 수정 화면으로 보내고, 서비스가 삭제 상태가 아니라고 거부하면 409로 응답한다. */
    @Test
    void restoresOnlyDeletedPost() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));
        mockMvc.perform(post("/admin/posts/{id}/restore", POST_ID).with(writer()).with(csrf()))
                .andExpect(redirectedUrl("/admin/posts/" + POST_ID + "/edit?restored"));
        verify(postEditor).restore(eq(POST_ID), any());

        doThrow(new IllegalArgumentException("삭제한 글만 복구할 수 있습니다.")).when(postEditor).restore(eq(POST_ID), any());
        mockMvc.perform(post("/admin/posts/{id}/restore", POST_ID).with(writer()).with(csrf()))
                .andExpect(status().isConflict());
    }

    /** 복구하려는 글의 주소를 다른 글이 쓰고 있으면 안내를 띄울 수정 화면으로 보낸다. */
    @Test
    void redirectsWithNoticeOnRestoreSlugConflict() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));
        doThrow(new PostEditorService.SlugConflictException("slug")).when(postEditor).restore(eq(POST_ID), any());

        mockMvc.perform(post("/admin/posts/{id}/restore", POST_ID).with(writer()).with(csrf()))
                .andExpect(redirectedUrl("/admin/posts/" + POST_ID + "/edit?slugConflict"));
        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).param("slugConflict", "").with(writer()))
                .andExpect(content().string(containsString("다른 글이 쓰고 있어 복구하지 못했습니다.")));
    }

    /** 삭제한 글의 수정 화면은 입력을 잠그고 복구만 보여 주며, 삭제·이미지 영역을 숨긴다. */
    @Test
    void rendersDeletedPostReadOnly() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));

        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<fieldset class=\"admin-fields\" disabled=\"disabled\">"),
                        containsString("/admin/posts/" + POST_ID + "/restore"),
                        containsString("삭제됨"),
                        not(containsString("/admin/posts/" + POST_ID + "/delete")),
                        not(containsString("data-image-upload")))));
    }

    /** 삭제하지 않은 글의 수정 화면은 입력을 열어 두고 삭제 버튼을 보여 준다. */
    @Test
    void rendersActivePostEditable() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));

        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<fieldset class=\"admin-fields\">"),
                        containsString("/admin/posts/" + POST_ID + "/delete"),
                        not(containsString("/admin/posts/" + POST_ID + "/restore")))));
    }

    /** 목록 행은 상태 배지를 보여 주고, 페이지 링크는 상태 필터와 검색어를 유지한다. */
    @Test
    void rendersRowsAndKeepsFilterInPageLinks() throws Exception {
        var rows = List.of(
                new PostEditorService.AdminPostRow(POST_ID, "deleted-slug", "지운 글", false, true, false, null,
                        java.time.LocalDateTime.of(2026, 10, 16, 12, 0)));
        when(postEditor.findPage(eq(0), eq(AdminPostFilter.DELETED), eq("검색")))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 50), 120));

        mockMvc.perform(get("/admin/posts").with(writer()).param("status", "deleted").param("q", "검색"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("admin-badge is-deleted"),
                        containsString(">삭제됨<"),
                        containsString("page=2&amp;status=deleted&amp;q="),
                        not(containsString("/posts/deleted-slug")))));
    }

    /** 삭제한 글에는 이미지를 올릴 수 없다. */
    @Test
    void rejectsImageUploadToDeletedPost() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));

        mockMvc.perform(multipart("/admin/posts/{id}/images", POST_ID)
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}))
                        .with(writer()).with(csrf()))
                .andExpect(status().isConflict());

        verify(postImages, never()).upload(any(), any());
    }

    /** 수정 화면은 변경 이력을 최신 버전부터 보여 주고, 버전 보기로 연결한다. */
    @Test
    void rendersRevisionHistoryOnEditPage() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));
        when(revisions.findHistory(POST_ID)).thenReturn(List.of(
                new RevisionRow(2, "발행", java.time.LocalDateTime.of(2026, 10, 16, 12, 30), "admin"),
                new RevisionRow(1, "기록 시작 전", java.time.LocalDateTime.of(2026, 10, 1, 9, 0), null)));

        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("/admin/posts/" + POST_ID + "/revisions/2"),
                        containsString("2026.10.16 12:30"),
                        containsString(">기록 시작 전<"))));
    }

    /** 되돌린 뒤 수정 화면은 숫자 버전 번호만 안내 문구에 넣는다. */
    @Test
    void rendersRevertNoticeOnlyForNumericVersion() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));

        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).param("reverted", "3").with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("버전 3의 제목·요약·본문으로 되돌렸습니다.")));
        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).param("reverted", "가짜 안내").with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("가짜 안내"))));
    }

    /** 버전 보기는 현재와 다른 항목을 표시하고, 삭제한 글이면 되돌리기 버튼을 숨긴다. */
    @Test
    void rendersRevisionAndHidesRevertForDeletedPost() throws Exception {
        when(revisions.find(POST_ID, 1)).thenReturn(Optional.of(revisionView()));
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));
        String revertPath = "/admin/posts/" + POST_ID + "/revisions/1/revert";

        mockMvc.perform(get("/admin/posts/{id}/revisions/1", POST_ID).with(writer()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("옛 본문"), containsString("현재와 다름"),
                        containsString(revertPath))));

        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(true)));
        mockMvc.perform(get("/admin/posts/{id}/revisions/1", POST_ID).with(writer()))
                .andExpect(content().string(not(containsString(revertPath))));
        mockMvc.perform(get("/admin/posts/{id}/revisions/2", POST_ID).with(writer()))
                .andExpect(status().isNotFound());
    }

    /** 되돌리면 안내를 띄울 수정 화면으로 보내고, 없는 버전은 404, 서비스가 거부하면 409로 응답한다. */
    @Test
    void revertsToRevision() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(editView(false)));
        when(revisions.find(POST_ID, 1)).thenReturn(Optional.of(revisionView()));

        mockMvc.perform(post("/admin/posts/{id}/revisions/1/revert", POST_ID).with(writer()).with(csrf()))
                .andExpect(redirectedUrl("/admin/posts/" + POST_ID + "/edit?reverted=1"));
        verify(revisions).revert(eq(POST_ID), eq(1), any());

        mockMvc.perform(post("/admin/posts/{id}/revisions/2/revert", POST_ID).with(writer()).with(csrf()))
                .andExpect(status().isNotFound());
        verify(revisions, never()).revert(eq(POST_ID), eq(2), any());

        doThrow(new IllegalArgumentException("삭제한 글은 복구한 뒤에 되돌릴 수 있습니다.")).when(revisions).revert(eq(POST_ID), eq(1), any());
        mockMvc.perform(post("/admin/posts/{id}/revisions/1/revert", POST_ID).with(writer()).with(csrf()))
                .andExpect(status().isConflict());
    }

    /** 되돌리기도 CSRF 토큰이 없으면 거부한다. */
    @Test
    void rejectsRevertWithoutCsrf() throws Exception {
        mockMvc.perform(post("/admin/posts/{id}/revisions/1/revert", POST_ID).with(writer()))
                .andExpect(status().isForbidden());

        verify(revisions, never()).revert(any(), anyInt(), any());
    }

    /** 발행 시각 입력은 한국 시간으로 해석해 서비스에 넘기고, 예약 글은 예약 배지와 취소 버튼을 보여 준다. */
    @Test
    void passesPublishTimeAndRendersScheduledPost() throws Exception {
        when(postEditor.findForEdit(POST_ID)).thenReturn(Optional.of(new PostEditView(POST_ID, "slug", "제목", null, "본문",
                null, "", null, true, false, false, true, java.time.LocalDateTime.of(2026, 10, 20, 9, 0))));

        mockMvc.perform(post("/admin/posts/{id}", POST_ID).with(writer()).with(csrf())
                        .param("title", "제목").param("content", "본문").param("publishedAt", "2026-10-20T09:00")
                        .param("action", "save"))
                .andExpect(redirectedUrl("/admin/posts/" + POST_ID + "/edit?saved"));
        verify(postEditor).update(eq(POST_ID), org.mockito.ArgumentMatchers.argThat(command ->
                java.time.Instant.parse("2026-10-20T00:00:00Z").equals(command.publishAt())), any());

        mockMvc.perform(get("/admin/posts/{id}/edit", POST_ID).with(writer()))
                .andExpect(content().string(allOf(containsString("admin-badge is-scheduled"),
                        containsString("2026.10.20 09:00에 공개"), containsString("예약 취소"),
                        containsString("value=\"2026-10-20T09:00\""), not(containsString("공개 글 보기")))));
    }

    /** @return 현재 글과 본문만 다른 버전 */
    private static RevisionView revisionView() {
        return new RevisionView(1, "작성", java.time.LocalDateTime.of(2026, 10, 1, 9, 0), "admin",
                "제목", null, "옛 본문", false, false, true);
    }

    /** @param deleted 삭제 여부 @return 수정 화면에 쓰는 글 값 */
    private static PostEditView editView(boolean deleted) {
        return new PostEditView(POST_ID, "slug", "제목", null, "본문", null, "", null, false, deleted, false, false, null);
    }

    /** @return 글 관리 권한을 가진 로그인 사용자 */
    private static RequestPostProcessor writer() {
        return user("admin").authorities(new SimpleGrantedAuthority(BlogUserDetailsService.POST_WRITE));
    }
}
