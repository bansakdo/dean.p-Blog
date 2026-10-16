package com.deanp.blog.post.service;

import com.deanp.blog.post.service.PostEditCommand.PublishAction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 글 작성·수정 입력의 정규화와 검증 규칙을 확인한다. */
class PostEditCommandTest {

    /** 주소를 비우면 제목의 금지 문자를 지우고 공백을 하이픈으로 바꿔 만든다. */
    @Test
    void derivesSlugFromTitle() {
        PostEditCommand normalized = command("  Spring <Boot> 시작하기?  ", null).normalized();

        assertThat(normalized.title()).isEqualTo("Spring <Boot> 시작하기?");
        assertThat(normalized.slug()).isEqualTo("Spring-Boot-시작하기");
    }

    /** 직접 입력한 주소에 공백이나 금지 문자가 있으면 고쳐 쓰지 않고 거부한다. */
    @Test
    void rejectsSlugWithForbiddenCharacters() {
        assertThatThrownBy(() -> command("제목", "a b").normalized()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("제목", "a/b").normalized()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("제목", "a\"b").normalized()).isInstanceOf(IllegalArgumentException.class);
        assertThat(command("제목", " valid-slug ").normalized().slug()).isEqualTo("valid-slug");
    }

    /** 제목에서 주소로 쓸 글자가 남지 않거나 주소가 너무 길면 거부한다. */
    @Test
    void rejectsEmptyOrTooLongSlug() {
        assertThatThrownBy(() -> command("???", null).normalized()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("제목", "a".repeat(201)).normalized()).isInstanceOf(IllegalArgumentException.class);
    }

    /** 제목과 본문은 비울 수 없고 제목은 300자를 넘을 수 없다. */
    @Test
    void requiresTitleAndContent() {
        assertThatThrownBy(() -> command(" ", "slug").normalized()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("가".repeat(301), "slug").normalized()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PostEditCommand("제목", "slug", null, " ", null, List.of(), null, null).normalized())
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 태그는 빈 값과 대소문자만 다른 중복을 지우고 처음 입력한 표기와 순서를 유지한다. */
    @Test
    void normalizesTags() {
        PostEditCommand normalized = new PostEditCommand("제목", "slug", null, "본문", null,
                Arrays.asList(" Java ", "", "spring", "JAVA", "Spring "), null, null).normalized();

        assertThat(normalized.tagNames()).containsExactly("Java", "spring");
        assertThatThrownBy(() -> new PostEditCommand("제목", "slug", null, "본문", null,
                List.of("t".repeat(101)), null, null).normalized()).isInstanceOf(IllegalArgumentException.class);
    }

    /** 빈 요약은 null로 저장하고 공개 동작이 없으면 현재 상태를 유지한다. */
    @Test
    void defaultsSummaryAndAction() {
        PostEditCommand normalized = new PostEditCommand("제목", "slug", "  ", "본문", null, null, null, null).normalized();

        assertThat(normalized.summary()).isNull();
        assertThat(normalized.tagNames()).isEmpty();
        assertThat(normalized.action()).isEqualTo(PublishAction.KEEP);
    }

    /** @param title 제목 @param slug 주소 @return 본문만 채운 작성 명령 */
    private PostEditCommand command(String title, String slug) {
        return new PostEditCommand(title, slug, null, "본문", null, List.of(), null, PublishAction.KEEP);
    }
}
