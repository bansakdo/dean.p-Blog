package com.deanp.blog.tag.service;

import com.deanp.blog.post.service.Slugs;
import com.deanp.blog.tag.persistence.entity.PostTag;
import com.deanp.blog.tag.persistence.entity.PostTagId;
import com.deanp.blog.tag.persistence.entity.Tag;
import com.deanp.blog.tag.persistence.repository.PostTagRepository;
import com.deanp.blog.tag.persistence.repository.PostTagRepository.TagCount;
import com.deanp.blog.tag.persistence.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 관리 화면의 태그 목록, 이름 변경, 병합, 삭제를 트랜잭션으로 조합한다.
 */
@Service
@RequiredArgsConstructor
public class TagAdminService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SLUG_LENGTH = 150;

    private final TagRepository tags;
    private final PostTagRepository postTags;

    /**
     * @return 모든 태그와 각 태그가 붙은 글 수(삭제한 글 포함), 대소문자를 구분하지 않은 이름순
     */
    @Transactional(readOnly = true)
    public List<TagRow> findAll() {
        Map<UUID, Long> counts = postTags.countByTag().stream()
                .collect(Collectors.toMap(TagCount::tagId, TagCount::count));
        return tags.findAll().stream()
                .sorted(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER))
                .map(tag -> new TagRow(tag.getId(), tag.getName(), tag.getSlug(), counts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    /**
     * @param id 태그 식별자
     * @return 태그와 붙은 글 수, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<TagRow> find(UUID id) {
        return tags.findById(id).map(tag -> new TagRow(tag.getId(), tag.getName(), tag.getSlug(),
                postTags.countByIdTagId(id)));
    }

    /**
     * 태그 이름과 주소를 바꾼다. 주소를 비우면 이름으로 만들고, 주소를 바꾸면 기존 태그 필터 링크가 끊긴다.
     *
     * @param id 태그 식별자
     * @param name 새 이름
     * @param slug 새 주소 또는 빈 값
     */
    @Transactional
    public void rename(UUID id, String name, String slug) {
        Tag tag = require(id);
        // 글 편집기는 태그를 쉼표로 이어 보여 주고 다시 쉼표로 나누므로 이름에 쉼표가 있으면 쪼개진다.
        String normalizedName = Slugs.name(name, MAX_NAME_LENGTH);
        if (normalizedName.contains(",")) {
            throw new IllegalArgumentException("태그 이름에는 쉼표를 쓸 수 없습니다.");
        }
        String normalizedSlug = Slugs.of(slug, normalizedName, MAX_SLUG_LENGTH);
        if (tags.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new IllegalArgumentException("이미 있는 태그 이름입니다. 합치려면 병합을 쓰세요.");
        }
        if (tags.existsBySlugAndIdNot(normalizedSlug, id)) {
            throw new IllegalArgumentException("이미 사용 중인 태그 주소(slug)입니다.");
        }
        tag.rename(normalizedName, normalizedSlug);
    }

    /**
     * 한 태그가 붙은 글에 대상 태그를 붙이고 원래 태그를 지운다. 이미 대상 태그가 붙은 글은 그대로 둔다.
     *
     * @param sourceId 없앨 태그 식별자
     * @param targetId 남길 태그 식별자
     */
    @Transactional
    public void merge(UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("같은 태그로는 병합할 수 없습니다.");
        }
        Tag source = require(sourceId);
        require(targetId);

        // 대상 태그가 아직 없는 글에만 연결을 새로 만든 뒤 원래 태그와 연결을 지운다.
        List<PostTag> sourceLinks = postTags.findByIdTagId(sourceId);
        Set<UUID> targetPosts = postTags.findByIdTagId(targetId).stream()
                .map(link -> link.getId().getPostDetailId()).collect(Collectors.toSet());
        sourceLinks.stream().map(link -> link.getId().getPostDetailId()).filter(postId -> !targetPosts.contains(postId))
                .forEach(postId -> postTags.save(PostTag.create(new PostTagId(postId, targetId))));
        postTags.deleteAll(sourceLinks);
        tags.delete(source);
    }

    /**
     * 태그와 글 연결을 지운다. 글은 그대로 남는다.
     *
     * @param id 태그 식별자
     */
    @Transactional
    public void delete(UUID id) {
        Tag tag = require(id);
        postTags.deleteAll(postTags.findByIdTagId(id));
        tags.delete(tag);
    }

    /**
     * @param id 태그 식별자
     * @return 태그, 없으면 예외
     */
    private Tag require(UUID id) {
        return tags.findById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다."));
    }

    /**
     * 태그 관리 목록의 한 줄이다.
     *
     * @param id 태그 식별자
     * @param name 표시 이름
     * @param slug URL 식별자
     * @param postCount 태그가 붙은 글 수(삭제한 글 포함)
     */
    public record TagRow(UUID id, String name, String slug, long postCount) {
    }
}
