package com.deanp.blog;

import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorDailySummaryRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorEventRepository;
import com.deanp.blog.visitor.persistence.repository.VisitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DeanPBlogApplicationTests {

	@MockitoBean
	private PostDetailRepository postDetailRepository;

	@MockitoBean
	private VisitorRepository visitorRepository;

	@MockitoBean
	private VisitorEventRepository visitorEventRepository;

	@MockitoBean
	private VisitorDailySummaryRepository visitorDailySummaryRepository;

	@Test
	void contextLoads() {
	}

}
