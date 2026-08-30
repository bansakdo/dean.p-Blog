package com.deanp.blog.post;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class SamplePostRepository {

    private final List<PostView> posts = List.of(
            new PostView(
                    "building-dean-p",
                    "dean.p를 만들기 시작했습니다",
                    "오래 유지할 수 있는 개인 블로그를 설계하며 내린 첫 번째 결정들을 기록합니다.",
                    LocalDate.of(2026, 8, 30),
                    5,
                    List.of("Spring Boot", "블로그"),
                    List.of(
                            "글을 쓰는 과정은 단순해야 하고, 읽는 화면은 조용해야 합니다. dean.p는 이 두 가지 기준에서 시작했습니다.",
                            "초기 버전은 Spring Boot의 서버 렌더링과 PostgreSQL을 사용합니다. 콘텐츠는 별도 Git 저장소에서 관리하고, Obsidian은 편안한 작성 도구로만 사용합니다.",
                            "지금 보이는 글은 화면을 검증하기 위한 샘플입니다. 다음 단계에서 Markdown 발행 파이프라인과 실제 저장소를 연결할 예정입니다."
                    )),
            new PostView(
                    "notes-that-last",
                    "오래 남는 기록의 조건",
                    "도구보다 중요한 것은 다시 찾고 고칠 수 있는 구조였습니다.",
                    LocalDate.of(2026, 8, 24),
                    4,
                    List.of("기록", "Obsidian"),
                    List.of(
                            "좋은 기록은 완벽한 문장보다 명확한 맥락을 남깁니다.",
                            "Markdown과 Git을 함께 사용하면 특정 편집기에 묶이지 않으면서 변경 이력도 보존할 수 있습니다."
                    )),
            new PostView(
                    "small-systems",
                    "작은 시스템을 운영하는 법",
                    "기능을 늘리기 전에 복구 가능한 구조부터 준비합니다.",
                    LocalDate.of(2026, 8, 12),
                    6,
                    List.of("운영", "MVP"),
                    List.of(
                            "개인 프로젝트도 운영을 시작하는 순간 작은 제품이 됩니다.",
                            "배포와 백업, 복구 절차를 먼저 단순하게 만들면 이후의 선택지가 넓어집니다."
                    ))
    );

    public List<PostView> findAll() {
        return posts;
    }

    public Optional<PostView> findBySlug(String slug) {
        return posts.stream().filter(post -> post.slug().equals(slug)).findFirst();
    }
}
