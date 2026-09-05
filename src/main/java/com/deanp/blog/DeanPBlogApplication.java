package com.deanp.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * dean.p 블로그 Spring Boot 애플리케이션의 진입점을 제공한다.
 */
@SpringBootApplication
public class DeanPBlogApplication {

	/**
	 * Spring Boot 런타임을 시작한다.
	 *
	 * @param args 명령행에서 전달된 애플리케이션 실행 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(DeanPBlogApplication.class, args);
	}

}
