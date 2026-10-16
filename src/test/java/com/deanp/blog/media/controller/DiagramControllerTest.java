package com.deanp.blog.media.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.media.service.PostDiagramService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 다이어그램 HTML을 로그인 없이, 블로그와 격리하는 헤더와 함께 내보내는지 검증한다. */
@WebMvcTest(DiagramController.class)
@Import(SecurityConfiguration.class)
class DiagramControllerTest {

    private static final UUID ID = new UUID(0, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostDiagramService diagrams;

    /** CSP sandbox(같은 출처 권한 없음)와 같은 출처 iframe만 허용하는 헤더를 붙인다. */
    @Test
    void servesDiagramWithSandboxHeaders() throws Exception {
        when(diagrams.read(ID)).thenReturn(Optional.of("<html>다이어그램</html>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/media/diagrams/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string("<html>다이어그램</html>"))
                .andExpect(header().string("Content-Security-Policy", DiagramController.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    /** 없는 다이어그램은 404다. */
    @Test
    void returnsNotFoundForMissingDiagram() throws Exception {
        when(diagrams.read(ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/media/diagrams/{id}", ID)).andExpect(status().isNotFound());
    }
}
