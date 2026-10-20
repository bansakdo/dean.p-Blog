package com.deanp.blog.post.service;

import com.deanp.blog.post.persistence.entity.PostCategory;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.entity.PostSeries;
import com.deanp.blog.post.persistence.repository.PostCategoryRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.persistence.repository.PostSeriesRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository.KeyCount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시리즈 조회, 생성, 수정, 삭제, 게시글 배치 규칙을 트랜잭션으로 조합한다.
 */
@Service
@RequiredArgsConstructor
public class PostSeriesService {
    private static final String DELETED = "DELETED";

    private final PostCategoryRepository categories;
    private final PostSeriesRepository series;
    private final PostDetailRepository postDetails;

    /**
     * 대표 카테고리를 가진 새 시리즈를 만든다.
     *
     * @param command 생성할 시리즈 값
     * @return 생성된 시리즈 식별자
     */
    @Transactional
    public UUID createSeries(PostSeriesCommand command) {
        PostSeriesCommand normalized = command.normalized();
        requireCategory(normalized.categoryId());
        validateNewSlug(normalized.slug());

        PostSeries saved = series.save(PostSeries.create(
                normalized.categoryId(),
                normalized.slug(),
                normalized.name(),
                normalized.description(),
                normalized.sortOrder(),
                normalized.status()
        ));
        return saved.getId();
    }

    /**
     * 시리즈 표시 정보와 대표 카테고리를 변경하고 연결 게시글의 카테고리를 동기화한다.
     *
     * @param seriesId 변경할 시리즈 식별자
     * @param command 변경할 시리즈 값
     */
    @Transactional
    public void updateSeries(UUID seriesId, PostSeriesCommand command) {
        PostSeries target = requireSeries(seriesId);
        PostSeriesCommand normalized = command.normalized();
        requireCategory(normalized.categoryId());
        validateExistingSlug(target, normalized.slug());

        target.update(
                normalized.categoryId(),
                normalized.slug(),
                normalized.name(),
                normalized.description(),
                normalized.sortOrder(),
                normalized.status()
        );
        // 시리즈 글은 대표 카테고리와 그 게시판을 따른다.
        UUID boardId = requireCategory(target.getCategoryId()).getBoardId();
        postDetails.findBySeriesId(target.getId()).forEach(post -> {
            post.changeCategory(target.getCategoryId());
            if (!boardId.equals(post.getBoardId())) {
                post.changeBoard(boardId);
            }
        });
    }

    /**
     * 게시글을 한 시리즈에 배치하고 게시글 카테고리를 시리즈 대표 카테고리로 맞춘다.
     *
     * @param seriesId 시리즈 식별자
     * @param postDetailId 게시글 식별자
     * @param seriesOrder 시리즈 안 순서
     */
    @Transactional
    public void assignPost(UUID seriesId, UUID postDetailId, int seriesOrder) {
        if (seriesOrder <= 0) {
            throw new IllegalArgumentException("시리즈 순서는 1 이상이어야 합니다.");
        }

        PostSeries target = requireSeries(seriesId);
        PostDetail post = requirePostDetail(postDetailId);
        if (DELETED.equals(post.getStatus())) {
            throw new IllegalArgumentException("삭제한 글은 시리즈에 넣을 수 없습니다.");
        }
        validateMembership(target, post);
        validateSeriesOrder(target, post, seriesOrder);
        if (!requireCategory(target.getCategoryId()).getBoardId().equals(post.getBoardId())) {
            throw new IllegalArgumentException("다른 게시판의 시리즈에는 넣을 수 없습니다. 글의 게시판을 먼저 바꾸세요.");
        }

        post.assignToSeries(target, seriesOrder);
    }

    /**
     * 게시글의 시리즈 연결을 제거한다.
     *
     * @param postDetailId 게시글 식별자
     */
    @Transactional
    public void removePost(UUID postDetailId) {
        requirePostDetail(postDetailId).removeFromSeries();
    }

    /**
     * 시리즈를 지운다. 삭제하지 않은 글이 속해 있으면 거부하고, 삭제한 글만 있으면 연결을 끊은 뒤 지운다.
     *
     * @param seriesId 시리즈 식별자
     */
    @Transactional
    public void deleteSeries(UUID seriesId) {
        PostSeries target = requireSeries(seriesId);
        List<PostDetail> members = postDetails.findBySeriesId(seriesId);
        long activePosts = members.stream().filter(post -> !DELETED.equals(post.getStatus())).count();
        if (activePosts > 0) {
            throw new IllegalArgumentException("글 " + activePosts + "편이 이 시리즈에 속해 있어 삭제할 수 없습니다. 먼저 시리즈에서 빼세요.");
        }
        members.forEach(PostDetail::removeFromSeries);
        series.delete(target);
    }

    /**
     * @return 모든 시리즈와 대표 카테고리 이름, 삭제하지 않은 글 수, 정렬 순서와 이름순
     */
    @Transactional(readOnly = true)
    public List<SeriesRow> findAll() {
        Map<UUID, Long> postCounts = postDetails.countBySeriesExcludingStatus(DELETED).stream()
                .collect(Collectors.toMap(KeyCount::id, KeyCount::count));
        Map<UUID, String> categoryNames = categories.findAll().stream()
                .collect(Collectors.toMap(PostCategory::getId, PostCategory::getName));
        return series.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(item -> new SeriesRow(item.getId(), item.getName(), item.getSlug(),
                        categoryNames.get(item.getCategoryId()), item.getStatus(), item.getSortOrder(),
                        postCounts.getOrDefault(item.getId(), 0L)))
                .toList();
    }

    /**
     * @param seriesId 시리즈 식별자
     * @return 수정 화면에 채울 시리즈 값, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<PostSeriesCommand> findForEdit(UUID seriesId) {
        return series.findById(seriesId).map(item -> new PostSeriesCommand(item.getCategoryId(), item.getSlug(),
                item.getName(), item.getDescription(), item.getSortOrder(), item.getStatus()));
    }

    /**
     * @param seriesId 시리즈 식별자
     * @return 시리즈에 속한 글을 시리즈 순서대로 나열한 목록
     */
    @Transactional(readOnly = true)
    public List<SeriesMember> findMembers(UUID seriesId) {
        return postDetails.findBySeriesIdOrderBySeriesOrderAsc(seriesId).stream()
                .map(post -> new SeriesMember(post.getId(), post.getTitle(), post.getStatus(), post.getSeriesOrder(),
                        scheduled(post)))
                .toList();
    }

    /**
     * @return 어느 시리즈에도 속하지 않은 삭제하지 않은 글, 제목순
     */
    @Transactional(readOnly = true)
    public List<SeriesMember> findCandidates() {
        return postDetails.findBySeriesIdIsNullAndStatusNotOrderByTitleAsc(DELETED).stream()
                .map(post -> new SeriesMember(post.getId(), post.getTitle(), post.getStatus(), null, scheduled(post)))
                .toList();
    }

    /**
     * @param post 게시글
     * @return 공개 상태이지만 발행 시각이 아직 오지 않은 예약 글인지 여부
     */
    private static boolean scheduled(PostDetail post) {
        return "PUBLISHED".equals(post.getStatus()) && post.getPublishedAt() != null
                && post.getPublishedAt().isAfter(Instant.now());
    }

    /**
     * 카테고리를 조회하거나 명시적인 예외를 던진다.
     *
     * @param categoryId 카테고리 식별자
     * @return 조회된 카테고리
     */
    private PostCategory requireCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("시리즈 대표 카테고리는 필수입니다.");
        }
        return categories.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));
    }

    /**
     * 시리즈를 조회하거나 명시적인 예외를 던진다.
     *
     * @param seriesId 시리즈 식별자
     * @return 조회된 시리즈
     */
    private PostSeries requireSeries(UUID seriesId) {
        if (seriesId == null) {
            throw new IllegalArgumentException("시리즈 식별자는 필수입니다.");
        }
        return series.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시리즈입니다."));
    }

    /**
     * 저장과 시리즈 배치가 서로의 변경을 덮어쓰지 않도록 게시글 행을 잠그고 조회하거나 명시적인 예외를 던진다.
     *
     * @param postDetailId 게시글 식별자
     * @return 조회된 게시글
     */
    private PostDetail requirePostDetail(UUID postDetailId) {
        if (postDetailId == null) {
            throw new IllegalArgumentException("게시글 식별자는 필수입니다.");
        }
        return postDetails.findForUpdateById(postDetailId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
    }

    /**
     * 새 시리즈 slug 중복 여부를 검증한다.
     *
     * @param slug URL 식별자
     */
    private void validateNewSlug(String slug) {
        if (series.existsBySlug(slug)) {
            throw new IllegalArgumentException("이미 사용 중인 시리즈 slug입니다.");
        }
    }

    /**
     * 기존 시리즈 수정 시 자기 자신을 제외한 slug 중복 여부를 검증한다.
     *
     * @param target 수정 대상 시리즈
     * @param slug URL 식별자
     */
    private void validateExistingSlug(PostSeries target, String slug) {
        if (series.existsBySlugAndIdNot(slug, target.getId())) {
            throw new IllegalArgumentException("이미 사용 중인 시리즈 slug입니다.");
        }
    }

    /**
     * 게시글이 이미 다른 시리즈에 속해 있지 않은지 검증한다.
     *
     * @param target 배치 대상 시리즈
     * @param post 게시글 엔티티
     */
    private void validateMembership(PostSeries target, PostDetail post) {
        if (post.getSeriesId() != null && !target.getId().equals(post.getSeriesId())) {
            throw new IllegalArgumentException("게시글은 하나의 시리즈에만 속할 수 있습니다.");
        }
    }

    /**
     * 같은 시리즈 안의 순서 중복을 검증한다.
     *
     * @param target 배치 대상 시리즈
     * @param post 게시글 엔티티
     * @param seriesOrder 요청 순서
     */
    private void validateSeriesOrder(PostSeries target, PostDetail post, int seriesOrder) {
        if (postDetails.existsBySeriesIdAndSeriesOrderAndIdNot(target.getId(), seriesOrder, post.getId())) {
            throw new IllegalArgumentException("시리즈 안에서 게시글 순서는 중복될 수 없습니다.");
        }
    }

    /**
     * 시리즈 관리 목록의 한 줄이다.
     *
     * @param id 시리즈 식별자
     * @param name 표시 이름
     * @param slug URL 식별자
     * @param categoryName 대표 카테고리 이름
     * @param status 상태: ACTIVE 연재 중, COMPLETED 완결, HIDDEN 숨김
     * @param sortOrder 정렬 순서
     * @param postCount 속한 삭제하지 않은 글 수
     */
    public record SeriesRow(UUID id, String name, String slug, String categoryName, String status, int sortOrder,
                            long postCount) {
    }

    /**
     * 시리즈에 속했거나 넣을 수 있는 글이다.
     *
     * @param id 게시글 식별자
     * @param title 제목
     * @param status 글 상태
     * @param seriesOrder 시리즈 안 순서, 아직 속하지 않았으면 null
     * @param scheduled 발행 시각이 아직 오지 않은 예약 글인지 여부
     */
    public record SeriesMember(UUID id, String title, String status, Integer seriesOrder, boolean scheduled) {
    }
}
