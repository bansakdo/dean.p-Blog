package com.deanp.blog.media.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.media.service.PostDiagramService;
import com.deanp.blog.media.service.PostDiagramService.DiagramFile;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 다이어그램 HTML을 로그인 없이, 블로그와 격리하는 헤더와 함께 내보내고, 다른 응답은 iframe 삽입을 막는지 검증한다. */
@WebMvcTest(DiagramController.class)
@Import(SecurityConfiguration.class)
class DiagramControllerTest {

    private static final UUID ID = new UUID(0, 5);
    private static final String CHECKSUM = "a".repeat(64);

    @TempDir
    Path root;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostDiagramService diagrams;

    /** CSP sandbox(같은 출처 권한 없음), 같은 출처 iframe만 허용하는 헤더, 오래 두는 캐시와 ETag를 붙인다. */
    @Test
    void servesDiagramWithSandboxHeaders() throws Exception {
        when(diagrams.find(ID)).thenReturn(Optional.of(new DiagramFile(file(), CHECKSUM)));

        mockMvc.perform(get("/media/diagrams/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string("<html>다이어그램</html>"))
                .andExpect(header().string("Content-Security-Policy", DiagramController.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "\""));
    }

    /** 같은 ETag로 다시 요청하면 본문 없이 304로 답한다. */
    @Test
    void answersNotModifiedForSameEtag() throws Exception {
        when(diagrams.find(ID)).thenReturn(Optional.of(new DiagramFile(file(), CHECKSUM)));

        mockMvc.perform(get("/media/diagrams/{id}", ID).header("If-None-Match", "\"" + CHECKSUM + "\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    /** 없는 다이어그램은 404이며, 오류 응답은 iframe 삽입을 막는다. */
    @Test
    void returnsNotFoundForMissingDiagramWithFrameDeny() throws Exception {
        when(diagrams.find(ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/media/diagrams/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    /** 다이어그램이 아닌 경로(정적 파일 포함)는 계속 iframe 삽입을 막는다. */
    @Test
    void keepsFrameDenyOnOtherPaths() throws Exception {
        mockMvc.perform(get("/css/site.css"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    /** @return 다이어그램 내용을 담은 임시 파일 */
    private Path file() throws Exception {
        return Files.writeString(root.resolve("d.html"), "<html>다이어그램</html>");
    }
}
