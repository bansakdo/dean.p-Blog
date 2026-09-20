package com.deanp.blog.post.service;

import com.deanp.blog.post.persistence.entity.PostCategory;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.entity.PostSeries;
import com.deanp.blog.post.persistence.repository.PostCategoryRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.persistence.repository.PostRepository;
import com.deanp.blog.post.persistence.repository.PostSeriesRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시리즈 생성, 수정, 게시글 배치 규칙을 트랜잭션으로 조합한다.
 */
@Service
@RequiredArgsConstructor
public class PostSeriesService {

    private final PostRepository posts;
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
        validatePostExists(normalized.postId());
        PostCategory category = requireCategory(normalized.categoryId());
        validateCategoryOwnership(normalized.postId(), category);
        validateNewSlug(normalized.postId(), normalized.slug());

        PostSeries saved = series.save(PostSeries.create(
                normalized.postId(),
                normalized.categoryId(),
                normalized.slug(),
                normalized.name(),
                normalized.description(),
                normalized.sortOrder()
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
        PostSeriesCommand normalized = command.normalizedForPost(target.getPostId());
        PostCategory category = requireCategory(normalized.categoryId());
        validateCategoryOwnership(target.getPostId(), category);
        validateExistingSlug(target, normalized.slug());

        target.update(
                normalized.categoryId(),
                normalized.slug(),
                normalized.name(),
                normalized.description(),
                normalized.sortOrder()
        );
        postDetails.findBySeriesId(target.getId())
                .forEach(post -> post.changeCategory(target.getCategoryId()));
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
        validatePostOwnership(target, post);
        validateMembership(target, post);
        validateSeriesOrder(target, post, seriesOrder);

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
     * 게시판 존재 여부를 검증한다.
     *
     * @param postId 게시판 식별자
     */
    private void validatePostExists(UUID postId) {
        if (postId == null || !posts.existsById(postId)) {
            throw new IllegalArgumentException("존재하지 않는 게시판입니다.");
        }
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
     * 게시글을 조회하거나 명시적인 예외를 던진다.
     *
     * @param postDetailId 게시글 식별자
     * @return 조회된 게시글
     */
    private PostDetail requirePostDetail(UUID postDetailId) {
        if (postDetailId == null) {
            throw new IllegalArgumentException("게시글 식별자는 필수입니다.");
        }
        return postDetails.findById(postDetailId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
    }

    /**
     * 카테고리가 같은 게시판에 속하는지 검증한다.
     *
     * @param postId 게시판 식별자
     * @param category 카테고리 엔티티
     */
    private void validateCategoryOwnership(UUID postId, PostCategory category) {
        if (!postId.equals(category.getPostId())) {
            throw new IllegalArgumentException("시리즈 카테고리는 같은 게시판에 속해야 합니다.");
        }
    }

    /**
     * 게시글이 시리즈와 같은 게시판에 속하는지 검증한다.
     *
     * @param target 시리즈 엔티티
     * @param post 게시글 엔티티
     */
    private void validatePostOwnership(PostSeries target, PostDetail post) {
        if (!target.getPostId().equals(post.getPostId())) {
            throw new IllegalArgumentException("다른 게시판의 게시글은 시리즈에 배치할 수 없습니다.");
        }
    }

    /**
     * 새 시리즈 slug 중복 여부를 검증한다.
     *
     * @param postId 게시판 식별자
     * @param slug URL 식별자
     */
    private void validateNewSlug(UUID postId, String slug) {
        if (series.existsByPostIdAndSlug(postId, slug)) {
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
        if (series.existsByPostIdAndSlugAndIdNot(target.getPostId(), slug, target.getId())) {
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
}
