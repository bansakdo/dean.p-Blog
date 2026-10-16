package com.deanp.blog.post.service;

import com.deanp.blog.post.persistence.entity.PostCategory;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.entity.PostSeries;
import com.deanp.blog.post.persistence.repository.PostCategoryRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.post.persistence.repository.PostDetailRepository.KeyCount;
import com.deanp.blog.post.persistence.repository.PostSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 관리 화면의 카테고리 목록, 생성, 수정, 삭제를 트랜잭션으로 조합한다.
 */
@Service
@RequiredArgsConstructor
public class CategoryAdminService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SLUG_LENGTH = 150;
    private static final String DELETED = "DELETED";

    private final PostCategoryRepository categories;
    private final PostDetailRepository posts;
    private final PostSeriesRepository series;

    /**
     * @return 모든 카테고리와 각 카테고리를 쓰는 삭제하지 않은 글·시리즈 수, 정렬 순서와 이름순
     */
    @Transactional(readOnly = true)
    public List<CategoryRow> findAll() {
        Map<UUID, Long> postCounts = posts.countByCategoryExcludingStatus(DELETED).stream()
                .collect(Collectors.toMap(KeyCount::id, KeyCount::count));
        Map<UUID, Long> seriesCounts = series.findAll().stream()
                .collect(Collectors.groupingBy(PostSeries::getCategoryId, Collectors.counting()));
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(category -> new CategoryRow(category.getId(), category.getName(), category.getSlug(),
                        category.getSortOrder(), postCounts.getOrDefault(category.getId(), 0L),
                        seriesCounts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    /**
     * 새 카테고리를 만든다. 주소를 비우면 이름으로 만든다.
     *
     * @param input 카테고리 입력 값
     * @return 만들어진 카테고리 식별자
     */
    @Transactional
    public UUID create(CategoryInput input) {
        String name = Slugs.name(input.name(), MAX_NAME_LENGTH);
        String slug = Slugs.of(input.slug(), name, MAX_SLUG_LENGTH);
        if (categories.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("이미 있는 카테고리 이름입니다.");
        }
        if (categories.existsBySlug(slug)) {
            throw new IllegalArgumentException("이미 사용 중인 카테고리 주소(slug)입니다.");
        }
        return categories.save(PostCategory.create(name, slug, input.sortOrder())).getId();
    }

    /**
     * 카테고리의 이름, 주소, 정렬 순서를 바꾼다. 주소를 바꾸면 기존 카테고리 필터 링크가 끊긴다.
     *
     * @param id 카테고리 식별자
     * @param input 카테고리 입력 값
     */
    @Transactional
    public void update(UUID id, CategoryInput input) {
        PostCategory category = require(id);
        String name = Slugs.name(input.name(), MAX_NAME_LENGTH);
        String slug = Slugs.of(input.slug(), name, MAX_SLUG_LENGTH);
        requireUnique(name, slug, id);
        category.update(name, slug, input.sortOrder());
    }

    /**
     * 카테고리를 지운다. 삭제하지 않은 글이나 시리즈가 쓰고 있으면 거부하고,
     * 삭제한 글만 쓰고 있으면 그 글들의 카테고리를 비운 뒤 지운다.
     *
     * @param id 카테고리 식별자
     */
    @Transactional
    public void delete(UUID id) {
        PostCategory category = require(id);
        List<PostDetail> users = posts.findByCategoryId(id);
        long activePosts = users.stream().filter(post -> !DELETED.equals(post.getStatus())).count();
        long seriesCount = series.countByCategoryId(id);
        if (activePosts > 0 || seriesCount > 0) {
            throw new IllegalArgumentException("글 " + activePosts + "편과 시리즈 " + seriesCount
                    + "개가 이 카테고리를 쓰고 있어 삭제할 수 없습니다. 먼저 다른 카테고리로 옮기세요.");
        }
        if (categories.existsByParentId(id)) {
            throw new IllegalArgumentException("하위 카테고리가 있어 삭제할 수 없습니다.");
        }
        users.forEach(post -> post.changeCategory(null));
        categories.delete(category);
    }

    /**
     * @param id 카테고리 식별자
     * @return 카테고리, 없으면 예외
     */
    private PostCategory require(UUID id) {
        return categories.findById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));
    }

    /**
     * 다른 카테고리와 이름·주소가 겹치지 않는지 확인한다.
     *
     * @param name 이름
     * @param slug 주소
     * @param id 제외할 카테고리 식별자
     */
    private void requireUnique(String name, String slug, UUID id) {
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("이미 있는 카테고리 이름입니다.");
        }
        if (categories.existsBySlugAndIdNot(slug, id)) {
            throw new IllegalArgumentException("이미 사용 중인 카테고리 주소(slug)입니다.");
        }
    }

    /**
     * 카테고리 생성·수정 입력 값이다.
     *
     * @param name 표시 이름
     * @param slug URL 식별자, 비어 있으면 이름으로 만든다
     * @param sortOrder 목록 정렬 순서, 작을수록 앞
     */
    public record CategoryInput(String name, String slug, int sortOrder) {
    }

    /**
     * 카테고리 관리 목록의 한 줄이다.
     *
     * @param id 카테고리 식별자
     * @param name 표시 이름
     * @param slug URL 식별자
     * @param sortOrder 정렬 순서
     * @param postCount 이 카테고리를 쓰는 삭제하지 않은 글 수
     * @param seriesCount 이 카테고리를 대표 카테고리로 쓰는 시리즈 수
     */
    public record CategoryRow(UUID id, String name, String slug, int sortOrder, long postCount, long seriesCount) {
    }
}
