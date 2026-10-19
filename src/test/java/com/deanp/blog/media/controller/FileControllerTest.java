package com.deanp.blog.media.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.media.service.PostFileService;
import com.deanp.blog.media.service.PostFileService.DownloadFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 첨부 파일을 다운로드 전용 헤더로 내보내고, 로그인 아이디를 넘겨 받을 수 없는 파일은 404로 답하는지 검증한다. */
@WebMvcTest(FileController.class)
@Import(SecurityConfiguration.class)
class FileControllerTest {

    private static final UUID ID = new UUID(0, 21);
    private static final String CHECKSUM = "b".repeat(64);

    @TempDir
    Path root;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostFileService files;

    /** 원본 파일명으로 내려받게 하고, 브라우저가 열어 실행하지 못하게 하며, 공유 캐시에 두지 않는다. */
    @Test
    void servesFileAsDownload() throws Exception {
        when(files.findForDownload(eq(ID), isNull())).thenReturn(Optional.of(download()));

        mockMvc.perform(get("/media/files/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().string("print('hi')"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''%EC%98%88%EC%A0%9C.py")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", FileController.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "\""));
    }

    /** 로그인한 사용자의 아이디를 넘겨 작성자인지 확인하게 하고, 받을 수 없으면 404로 답한다. */
    @Test
    void passesLoginIdAndHidesUnavailableFiles() throws Exception {
        when(files.findForDownload(eq(ID), eq("admin"))).thenReturn(Optional.of(download()));

        mockMvc.perform(get("/media/files/{id}", ID).with(user("admin")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/media/files/{id}", ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/media/files/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    /** @return 임시 파일을 가리키는 다운로드 정보 */
    private DownloadFile download() throws Exception {
        Path file = Files.writeString(root.resolve(ID + ".py"), "print('hi')");
        return new DownloadFile(file, "예제.py", "text/plain", CHECKSUM);
    }
}
