package com.deanp.blog.post.service;

import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;
import com.deanp.blog.media.persistence.repository.PostAttachedFileRepository;
import com.deanp.blog.post.PostView;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.repository.PostCategoryRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository.StatusCount;
import com.deanp.blog.post.service.PostEditCommand.PublishAction;
import com.deanp.blog.publishing.service.PostRevisionService;
import com.deanp.blog.publishing.service.RevisionChangeType;
import com.deanp.blog.tag.persistence.entity.PostTag;
import com.deanp.blog.tag.persistence.entity.PostTagId;
import com.deanp.blog.tag.persistence.entity.Tag;
import com.deanp.blog.tag.persistence.repository.PostTagRepository;
import com.deanp.blog.tag.persistence.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 글 관리 화면의 목록 조회, 작성, 수정, 공개 상태 전환, 삭제·복구를 트랜잭션으로 조합한다.
 * 글을 바꿀 때마다 같은 트랜잭션에서 변경 이력을 남긴다.
 */
@Service
@RequiredArgsConstructor
public class PostEditorService {

    private static final int PAGE_SIZE = 50;
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DELETED = "DELETED";
    private static final String ACTIVE = "ACTIVE";
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final PostDetailRepository posts;
    private final PostCategoryRepository categories;
    private final TagRepository tags;
    private final PostTagRepository postTags;
    private final PostAttachedFileRepository postAttachedFiles;
    private final AppUserRepository users;
    private final PostService postService;
    private final PostRevisionService revisions;

    /**
     * 상태 필터와 제목 검색어에 맞는 글을 최근 수정순으로 조회한다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param filter 포함할 글 상태
     * @param query 제목 검색어, 비어 있으면 모든 제목
     * @return 관리 목록 한 페이지
     */
    @Transactional(readOnly = true)
    public Page<AdminPostRow> findPage(int page, AdminPostFilter filter, String query) {
        // 같은 시각에 수정한 글도 페이지마다 순서가 바뀌지 않도록 식별자로 한 번 더 정렬한다.
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id"));
        String title = query == null ? "" : query.strip();
        return posts.findByStatusInAndTitleContainingIgnoreCase(filter.getStatuses(), title,
                        PageRequest.of(page, PAGE_SIZE, sort))
                .map(PostEditorService::row);
    }

    /**
     * 대시보드에 보여 줄 상태별 글 수, 곧 발행할 예약 글, 최근에 고친 글을 모은다.
     *
     * @return 글 현황
     */
    @Transactional(readOnly = true)
    public PostSummary summarize() {
        Instant now = Instant.now();
        Map<String, Long> counts = posts.countByStatus().stream()
                .collect(Collectors.toMap(StatusCount::status, StatusCount::count));
        // 예약 글은 상태가 PUBLISHED이므로 공개 글 수에서 뺀다.
        long scheduled = posts.countByStatusAndPublishedAtAfter(PUBLISHED, now);
        return new PostSummary(
                counts.getOrDefault(PUBLISHED, 0L) - scheduled,
                scheduled,
                counts.getOrDefault("DRAFT", 0L),
                counts.getOrDefault(DELETED, 0L),
                posts.findTop5ByStatusAndPublishedAtAfterOrderByPublishedAtAsc(PUBLISHED, now).stream()
                        .map(PostEditorService::row).toList(),
                posts.findTop5ByStatusNotOrderByUpdatedAtDescIdAsc(DELETED).stream()
                        .map(PostEditorService::row).toList());
    }

    /**
     * @return 글에 지정할 수 있는 활성 카테고리 목록
     */
    @Transactional(readOnly = true)
    public List<CategoryChoice> findCategoryChoices() {
        return categories.findByStatusOrderBySortOrderAscNameAsc(ACTIVE).stream()
                .map(category -> new CategoryChoice(category.getId(), category.getName()))
                .toList();
    }

    /**
     * 수정 화면에 채울 글의 현재 값을 조회한다.
     *
     * @param id 게시글 식별자
     * @return 글의 현재 값, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<PostEditView> findForEdit(UUID id) {
        return posts.findById(id).map(post -> new PostEditView(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getSummary(),
                post.getContent(),
                post.getCategoryId(),
                String.join(", ", tagNames(post.getId())),
                post.getRepresentativeImageId(),
                PUBLISHED.equals(post.getStatus()),
                DELETED.equals(post.getStatus()),
                post.getSeriesId() != null,
                scheduled(post),
                displayTime(post.getPublishedAt())
        ));
    }

    /**
     * 저장된 글을 공개 여부와 관계없이 공개 화면과 같은 모양으로 변환한다.
     *
     * @param id 게시글 식별자
     * @return 미리보기용 표시 데이터, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<PostView> findPreview(UUID id) {
        return posts.findById(id).map(post -> postService.preview(post.getId(), post.getSlug(), post.getTitle(),
                post.getSummary(), post.getContent(), post.getPublishedAt(), tagNames(post.getId())));
    }

    /**
     * 새 글을 만든다.
     *
     * @param command 작성할 글 값
     * @param authorLoginId 작성자의 로그인 아이디
     * @return 만들어진 게시글 식별자
     */
    @Transactional
    public UUID create(PostEditCommand command, String authorLoginId) {
        PostEditCommand normalized = command.normalized();

        // 작성자와 카테고리, 주소 중복을 확인한다.
        UUID authorId = users.findByLoginId(authorLoginId)
                .orElseThrow(() -> new IllegalStateException("로그인한 사용자를 찾을 수 없습니다.")).getId();
        requireCategory(normalized.categoryId());
        if (posts.existsBySlugAndStatusNot(normalized.slug(), DELETED)) {
            throw new IllegalArgumentException("이미 사용 중인 주소(slug)입니다.");
        }

        // 초안으로 저장한 뒤 태그와 공개 상태를 반영한다. 새 글에는 아직 첨부 이미지가 없다.
        PostDetail post = posts.save(PostDetail.createDraft(authorId, normalized.categoryId(), normalized.slug(),
                normalized.title(), normalized.summary(), normalized.content()));
        replaceTags(post.getId(), normalized.tagNames());
        if (normalized.action() == PublishAction.PUBLISH) {
            post.publish(normalized.publishAt());
        }
        revisions.record(post, normalized.action() == PublishAction.PUBLISH
                ? RevisionChangeType.PUBLISH : RevisionChangeType.CREATE, authorLoginId);
        return post.getId();
    }

    /**
     * 기존 글의 내용과 분류, 대표 이미지, 공개 상태를 고친다.
     *
     * @param id 고칠 게시글 식별자
     * @param command 고칠 글 값
     * @param editorLoginId 고친 사용자의 로그인 아이디
     */
    @Transactional
    public void update(UUID id, PostEditCommand command, String editorLoginId) {
        PostDetail post = posts.findForUpdateById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        if (DELETED.equals(post.getStatus())) {
            throw new IllegalArgumentException("삭제한 글은 복구한 뒤에 고칠 수 있습니다.");
        }
        PostEditCommand normalized = command.normalized();

        // 삭제하지 않은 다른 글과 주소가 겹치지 않을 때만 본문 값을 고친다.
        if (posts.existsBySlugAndStatusNotAndIdNot(normalized.slug(), DELETED, id)) {
            throw new IllegalArgumentException("이미 사용 중인 주소(slug)입니다.");
        }
        revisions.recordBaselineIfMissing(post);
        post.edit(normalized.slug(), normalized.title(), normalized.summary(), normalized.content());

        // 시리즈에 속한 글의 카테고리는 시리즈 대표 카테고리를 따르므로 여기서 바꾸지 않는다.
        if (post.getSeriesId() == null) {
            requireCategory(normalized.categoryId());
            post.changeCategory(normalized.categoryId());
        }

        // 대표 이미지는 이 글에 첨부된 이미지만 지정할 수 있다. 다이어그램 같은 이미지가 아닌 첨부는 거부한다.
        if (normalized.representativeImageId() != null && postAttachedFiles
                .findById(new PostAttachedFileId(id, normalized.representativeImageId()))
                .filter(link -> link.getAttachedFile().isImage())
                .isEmpty()) {
            throw new IllegalArgumentException("이 글에 첨부된 이미지만 대표 이미지로 지정할 수 있습니다.");
        }
        post.changeRepresentativeImage(normalized.representativeImageId());

        // 태그 연결을 맞추고 요청한 공개 상태로 전환한다. 공개 상태가 실제로 바뀐 경우에만
        // 발행·비공개 전환으로 기록한다. 공개 중인 글을 그대로 저장할 때는 지정한 발행 시각만 반영한다.
        replaceTags(id, normalized.tagNames());
        boolean wasPublished = PUBLISHED.equals(post.getStatus());
        Instant publishAt = requestedPublishAt(post, normalized.publishAt());
        RevisionChangeType changeType = RevisionChangeType.UPDATE;
        if (normalized.action() == PublishAction.KEEP && wasPublished && publishAt != null
                && !publishAt.equals(post.getPublishedAt())) {
            post.reschedule(publishAt);
        } else if (normalized.action() == PublishAction.PUBLISH) {
            post.publish(publishAt);
            changeType = wasPublished ? RevisionChangeType.UPDATE : RevisionChangeType.PUBLISH;
        } else if (normalized.action() == PublishAction.UNPUBLISH) {
            post.unpublish();
            changeType = wasPublished ? RevisionChangeType.UNPUBLISH : RevisionChangeType.UPDATE;
        }
        revisions.record(post, changeType, editorLoginId);
    }

    /**
     * 글을 삭제 상태로 바꾼다. 공개 중이던 글도 바로 공개 화면에서 내려간다.
     *
     * @param id 삭제할 게시글 식별자
     * @param editorLoginId 삭제한 사용자의 로그인 아이디
     */
    @Transactional
    public void delete(UUID id, String editorLoginId) {
        PostDetail post = posts.findForUpdateById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        revisions.recordBaselineIfMissing(post);
        post.delete();
        revisions.record(post, RevisionChangeType.DELETE, editorLoginId);
    }

    /**
     * 삭제한 글을 초안으로 되돌린다. 삭제한 사이에 다른 글이 같은 주소를 쓰기 시작했다면 복구하지 않는다.
     *
     * @param id 복구할 게시글 식별자
     * @param editorLoginId 복구한 사용자의 로그인 아이디
     * @throws SlugConflictException 삭제하지 않은 다른 글이 같은 주소를 쓰고 있을 때
     */
    @Transactional
    public void restore(UUID id, String editorLoginId) {
        PostDetail post = posts.findForUpdateById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        if (!DELETED.equals(post.getStatus())) {
            throw new IllegalArgumentException("삭제한 글만 복구할 수 있습니다.");
        }
        if (posts.existsBySlugAndStatusNotAndIdNot(post.getSlug(), DELETED, id)) {
            throw new SlugConflictException(post.getSlug());
        }
        revisions.recordBaselineIfMissing(post);
        post.restore();
        revisions.record(post, RevisionChangeType.RESTORE, editorLoginId);
    }

    /**
     * @param postDetailId 게시글 식별자
     * @return 게시글에 붙은 태그 이름을 이름순으로 나열한 목록
     */
    private List<String> tagNames(UUID postDetailId) {
        return postTags.findByIdPostDetailId(postDetailId).stream()
                .map(postTag -> postTag.getTag().getName())
                .sorted()
                .toList();
    }

    /**
     * 폼은 분 단위로만 시각을 다루므로, 저장된 발행 시각과 같은 분을 다시 보내면 저장된 값을 그대로 쓴다.
     *
     * @param post 게시글
     * @param requested 폼에서 지정한 발행 시각 또는 null
     * @return 적용할 발행 시각 또는 null
     */
    private static Instant requestedPublishAt(PostDetail post, Instant requested) {
        Instant current = post.getPublishedAt();
        if (requested != null && current != null
                && requested.truncatedTo(ChronoUnit.MINUTES).equals(current.truncatedTo(ChronoUnit.MINUTES))) {
            return current;
        }
        return requested;
    }

    /**
     * @param post 게시글
     * @return 관리 목록의 글 한 줄
     */
    private static AdminPostRow row(PostDetail post) {
        return new AdminPostRow(post.getId(), post.getSlug(), post.getTitle(),
                PUBLISHED.equals(post.getStatus()), DELETED.equals(post.getStatus()), scheduled(post),
                displayTime(post.getPublishedAt()), displayTime(post.getUpdatedAt()));
    }

    /**
     * @param post 게시글
     * @return 공개 상태이지만 발행 시각이 아직 오지 않아 공개 화면에 나오지 않는지 여부
     */
    private static boolean scheduled(PostDetail post) {
        return PUBLISHED.equals(post.getStatus()) && post.getPublishedAt() != null
                && post.getPublishedAt().isAfter(Instant.now());
    }

    /**
     * @param instant 시각 또는 null
     * @return 화면 표시 시간대로 바꾼 시각 또는 null
     */
    private static LocalDateTime displayTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, DISPLAY_ZONE);
    }

    /**
     * 카테고리를 지정했다면 실제로 있는지 확인한다.
     *
     * @param categoryId 카테고리 식별자 또는 null
     */
    private void requireCategory(UUID categoryId) {
        if (categoryId != null && !categories.existsById(categoryId)) {
            throw new IllegalArgumentException("존재하지 않는 카테고리입니다.");
        }
    }

    /**
     * 게시글의 태그 연결을 요청한 이름 목록과 같게 맞춘다. 없는 태그는 새로 만든다.
     *
     * @param postDetailId 게시글 식별자
     * @param tagNames 정규화된 태그 이름 목록
     */
    private void replaceTags(UUID postDetailId, List<String> tagNames) {
        // 이름으로 태그를 찾고 없으면 만든다.
        Set<UUID> wanted = tagNames.stream()
                .map(name -> tags.findFirstByNameIgnoreCase(name).orElseGet(() -> createTag(name)).getId())
                .collect(Collectors.toSet());

        // 빠진 연결은 지우고 새 연결만 추가한다.
        List<PostTag> current = postTags.findByIdPostDetailId(postDetailId);
        postTags.deleteAll(current.stream().filter(link -> !wanted.contains(link.getId().getTagId())).toList());
        Set<UUID> linked = current.stream().map(link -> link.getId().getTagId()).collect(Collectors.toSet());
        wanted.stream().filter(tagId -> !linked.contains(tagId))
                .forEach(tagId -> postTags.save(PostTag.create(new PostTagId(postDetailId, tagId))));
    }

    /**
     * 이름에서 slug를 만들어 새 태그를 저장한다. 글 주소와 같은 금지 문자를 지운다.
     *
     * @param name 태그 이름
     * @return 저장된 태그
     */
    private Tag createTag(String name) {
        // 금지 문자를 지우고 공백을 하이픈으로 바꾼다. 남는 글자가 없으면 주소를 만들 수 없다.
        String base = PostEditCommand.WHITESPACE.matcher(
                PostEditCommand.SLUG_FORBIDDEN.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("").strip()
        ).replaceAll("-");
        if (base.isEmpty()) {
            throw new IllegalArgumentException("태그 '" + name + "'에는 주소로 쓸 수 있는 글자가 없습니다.");
        }

        // C#과 C처럼 이름은 다르지만 주소가 겹치면 -2, -3을 붙여 비어 있는 주소를 쓴다.
        String slug = base;
        for (int suffix = 2; tags.existsBySlug(slug); suffix++) {
            slug = base + "-" + suffix;
        }
        return tags.save(Tag.create(name, slug));
    }

    /**
     * 복구하려는 글의 주소를 삭제하지 않은 다른 글이 쓰고 있어 복구할 수 없을 때 던진다.
     */
    public static final class SlugConflictException extends RuntimeException {

        /** @param slug 겹친 주소 */
        public SlugConflictException(String slug) {
            super("주소(slug) '" + slug + "'를 다른 글이 쓰고 있어 복구할 수 없습니다. 그 글의 주소를 바꾼 뒤 다시 복구하세요.");
        }
    }

    /**
     * 대시보드의 글 현황이다.
     *
     * @param published 공개 중인 글 수
     * @param scheduled 발행을 기다리는 예약 글 수
     * @param drafts 초안 수
     * @param deleted 삭제한 글 수
     * @param upcoming 곧 발행할 예약 글
     * @param recent 최근에 고친 글
     */
    public record PostSummary(long published, long scheduled, long drafts, long deleted,
                              List<AdminPostRow> upcoming, List<AdminPostRow> recent) {
    }

    /**
     * 관리 목록의 글 한 줄이다.
     *
     * @param id 게시글 식별자
     * @param slug URL 식별자
     * @param title 제목
     * @param published 공개 여부
     * @param deleted 삭제 여부
     * @param scheduled 발행 시각이 아직 오지 않은 예약 글인지 여부
     * @param publishedAt 화면 표시 시간대로 바꾼 발행 시각 또는 null
     * @param updatedAt 화면 표시 시간대로 바꾼 마지막 수정 시각
     */
    public record AdminPostRow(UUID id, String slug, String title, boolean published, boolean deleted,
                               boolean scheduled, LocalDateTime publishedAt, LocalDateTime updatedAt) {
    }

    /**
     * 글에 지정할 수 있는 카테고리 선택지다.
     *
     * @param id 카테고리 식별자
     * @param name 표시 이름
     */
    public record CategoryChoice(UUID id, String name) {
    }

    /**
     * 수정 화면에 채울 글의 현재 값이다.
     *
     * @param id 게시글 식별자
     * @param slug URL 식별자
     * @param title 제목
     * @param summary 요약
     * @param content Markdown 본문
     * @param categoryId 카테고리 식별자 또는 null
     * @param tags 쉼표로 이은 태그 이름
     * @param representativeImageId 대표 이미지 파일 식별자 또는 null
     * @param published 공개 여부
     * @param deleted 삭제 여부
     * @param inSeries 시리즈에 속해 카테고리를 바꿀 수 없는지 여부
     * @param scheduled 발행 시각이 아직 오지 않은 예약 글인지 여부
     * @param publishedAt 화면 표시 시간대로 바꾼 발행 시각 또는 null
     */
    public record PostEditView(UUID id, String slug, String title, String summary, String content,
                               UUID categoryId, String tags, UUID representativeImageId,
                               boolean published, boolean deleted, boolean inSeries,
                               boolean scheduled, LocalDateTime publishedAt) {
    }
}
