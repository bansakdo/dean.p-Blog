package com.deanp.blog.seo.controller;

import com.deanp.blog.auth.controller.SecurityConfiguration;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 개발 서버처럼 색인을 허용하지 않는 서버는 전체 크롤링을 막고 사이트맵을 내지 않는다. */
@WebMvcTest(controllers = SearchEngineController.class, properties = "app.seo.indexable=false")
@Import(SecurityConfiguration.class)
class NonIndexableSearchEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostDetailRepository posts;

    /** robots.txt는 모든 경로를 막고, sitemap.xml은 404다. */
    @Test
    void blocksAllCrawlingAndHidesSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("User-agent: *\nDisallow: /\n"));
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isNotFound());
    }
}
