package com.deanp.blog.post.persistence.query;

import com.deanp.blog.post.persistence.entity.QPostCategory;
import com.deanp.blog.post.persistence.entity.QPostDetail;
import com.deanp.blog.post.persistence.entity.QPostSeries;
import com.deanp.blog.post.PostSeriesOption;
import com.deanp.blog.tag.persistence.entity.QPostTag;
import com.deanp.blog.tag.persistence.entity.QTag;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.deanp.blog.post.PostFilterOption;
import com.deanp.blog.media.persistence.entity.QAttachedFile;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

/**
 * Querydsl로 공개 게시글과 태그 표시 행을 조회한다.
 */
public class PublicPostQueryRepositoryImpl implements PublicPostQueryRepository {

    private static final String PUBLISHED = "PUBLISHED";

    private final JPAQueryFactory queryFactory;

    /**
     * EntityManager로 Querydsl 쿼리 팩토리를 구성한다.
     *
     * @param entityManager JPA 쿼리를 실행할 영속성 컨텍스트
     */
    public PublicPostQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    /**
     * 공개 글을 최신 발행일 순으로 조회하고 선택적으로 카테고리 조건을 적용한다.
     *
     * @param categorySlug 카테고리 슬러그, 비어 있으면 전체 공개 글
     * @return 화면 변환에 사용할 게시글-태그 행 목록
     */
    @Override
    public List<PublicPostRow> findPublishedRowsNewestFirst(String categorySlug) {
        return findPublishedRowsNewestFirst(categorySlug, null);
    }

    /**
     * 카테고리와 태그를 함께 적용하되 일치한 글의 다른 태그도 보존한다.
     * @param categorySlug 선택 카테고리
     * @param tagSlug 선택 태그
     * @return 최신순 공개 글과 태그 행
     */
    @Override
    public List<PublicPostRow> findPublishedRowsNewestFirst(String categorySlug, String tagSlug) {
        return findPublishedRows(categorySlug, null, tagSlug, null);
    }

    /**
     * 카테고리, 시리즈, 태그, 검색어를 함께 적용하되 일치한 글의 다른 태그도 보존한다.
     * @param categorySlug 선택 카테고리
     * @param seriesSlug 선택 시리즈
     * @param tagSlug 선택 태그
     * @param search 검색어
     * @return 공개 글과 태그 행
     */
    @Override
    public List<PublicPostRow> findPublishedRows(String categorySlug, String seriesSlug, String tagSlug, String search) {
        // Querydsl Q 타입을 준비해 게시글, 카테고리, 태그 조인 대상을 명확히 한다.
        QPostDetail postDetail = QPostDetail.postDetail;
        QPostCategory category = QPostCategory.postCategory;
        QPostSeries series = QPostSeries.postSeries;
        QPostTag postTag = QPostTag.postTag;
        QTag tag = QTag.tag;

        // 공개 글 기본 쿼리에 카테고리 슬러그 조건이 있을 때만 추가한다.
        JPAQuery<Tuple> query = fetchPublishedRows(postDetail, category, series, postTag, tag);
        BooleanExpression categoryFilter = categorySlugMatches(category, categorySlug);
        if (categoryFilter != null) {
            query.where(categoryFilter);
        }

        // 시리즈 조건은 알 수 없는 slug여도 생략하지 않고 빈 결과로 이어지게 한다.
        if (seriesSlug != null && !seriesSlug.isBlank()) {
            query.where(series.slug.eq(seriesSlug));
        }

        // 별도 EXISTS 조건으로 필터링해 화면에 표시할 태그 조인 행은 줄이지 않는다.
        if (tagSlug != null && !tagSlug.isBlank()) {
            QPostTag matchingPostTag = new QPostTag("matchingPostTag");
            QTag matchingTag = new QTag("matchingTag");
            query.where(JPAExpressions.selectOne().from(matchingPostTag)
                    .join(matchingTag).on(matchingTag.id.eq(matchingPostTag.id.tagId))
                    .where(matchingPostTag.id.postDetailId.eq(postDetail.id), matchingTag.slug.eq(tagSlug))
                    .exists());
        }

        BooleanExpression searchFilter = searchMatches(postDetail, search);
        if (searchFilter != null) {
            query.where(searchFilter);
        }

        // 정렬된 쿼리 결과를 공개 화면용 행 객체로 변환한다.
        if (seriesSlug != null && !seriesSlug.isBlank()) {
            query.orderBy(postDetail.seriesOrder.asc(), postDetail.publishedAt.desc(), postDetail.id.asc(), tag.name.asc());
        } else {
            query.orderBy(postDetail.publishedAt.desc(), postDetail.id.asc(), tag.name.asc());
        }

        return query
                .fetch()
                .stream()
                .map(row -> toPublicPostRow(row, postDetail, tag))
                .toList();
    }

    /** @return 미분류 글까지 포함하며 태그 조인에 중복되지 않는 공개 글 수 */
    @Override
    public long countPublishedPosts() {
        QPostDetail post = QPostDetail.postDetail;
        return queryFactory.select(post.id.count()).from(post)
                .where(post.status.eq(PUBLISHED), post.publishedAt.loe(java.time.Instant.now())).fetchOne();
    }

    /** @return 공개 글에서 사용하는 카테고리만 이름순으로 반환한다. */
    @Override
    public List<PostFilterOption> findPublishedCategories() {
        QPostDetail post = QPostDetail.postDetail;
        QPostCategory category = QPostCategory.postCategory;
        return queryFactory.select(Projections.constructor(PostFilterOption.class, category.slug, category.name, post.id.countDistinct()))
                .from(post).join(category).on(category.id.eq(post.categoryId))
                .where(post.status.eq(PUBLISHED), post.publishedAt.loe(java.time.Instant.now()))
                .groupBy(category.slug, category.name)
                .orderBy(category.name.asc(), category.slug.asc()).fetch();
    }

    /** @return 공개 글에서 사용하는 태그만 이름순으로 반환한다. */
    @Override
    public List<PostFilterOption> findPublishedTags() {
        QPostDetail post = QPostDetail.postDetail;
        QPostTag postTag = QPostTag.postTag;
        QTag tag = QTag.tag;
        return queryFactory.select(Projections.constructor(PostFilterOption.class, tag.slug, tag.name))
                .distinct().from(post).join(postTag).on(postTag.id.postDetailId.eq(post.id))
                .join(tag).on(tag.id.eq(postTag.id.tagId))
                .where(post.status.eq(PUBLISHED), post.publishedAt.loe(java.time.Instant.now()))
                .orderBy(tag.name.asc(), tag.slug.asc()).fetch();
    }

    /** 공개 글이 있는 시리즈만 선택지로 반환한다. */
    @Override
    public List<PostSeriesOption> findPublishedSeries(String categorySlug) {
        QPostDetail post = QPostDetail.postDetail;
        QPostSeries series = QPostSeries.postSeries;
        QPostCategory category = QPostCategory.postCategory;
        JPAQuery<PostSeriesOption> query = queryFactory
                .select(Projections.constructor(PostSeriesOption.class,
                        series.slug, series.name, series.description, category.slug,
                        post.id.countDistinct(), series.status))
                .from(post)
                .join(series).on(series.id.eq(post.seriesId))
                .join(category).on(category.id.eq(series.categoryId))
                .where(post.status.eq(PUBLISHED), post.publishedAt.loe(java.time.Instant.now()), series.status.in("ACTIVE", "COMPLETED"));

        BooleanExpression categoryFilter = categorySlugMatches(category, categorySlug);
        if (categoryFilter != null) {
            query.where(categoryFilter);
        }

        return query.groupBy(series.id, series.slug, series.name, series.description, category.slug, series.status)
                .orderBy(series.sortOrder.min().asc(), series.name.asc(), series.slug.asc()).fetch();
    }

    /** 공개 글이 있는 단일 시리즈 소개 정보를 반환한다. */
    @Override
    public Optional<PostSeriesOption> findPublishedSeries(String categorySlug, String seriesSlug) {
        if (seriesSlug == null || seriesSlug.isBlank()) {
            return Optional.empty();
        }

        return findPublishedSeries(categorySlug).stream()
                .filter(option -> option.slug().equals(seriesSlug)).findFirst();
    }

    /**
     * 슬러그가 일치하는 공개 글과 연결 태그 행을 조회한다.
     *
     * @param slug 게시글 슬러그
     * @return 같은 게시글의 태그별 조회 행 목록
     */
    @Override
    public List<PublicPostRow> findPublishedRowsBySlug(String slug) {
        // Querydsl Q 타입을 준비해 게시글 상세 조회에 필요한 조인 대상을 공유한다.
        QPostDetail postDetail = QPostDetail.postDetail;
        QPostCategory category = QPostCategory.postCategory;
        QPostSeries series = QPostSeries.postSeries;
        QPostTag postTag = QPostTag.postTag;
        QTag tag = QTag.tag;

        // 공개 글 기본 쿼리에 슬러그 조건을 더하고 태그명 순으로 결과를 안정화한다.
        return fetchPublishedRows(postDetail, category, series, postTag, tag)
                .where(postDetail.slug.eq(slug))
                .orderBy(tag.name.asc())
                .fetch()
                .stream()
                .map(row -> toPublicPostRow(row, postDetail, tag))
                .toList();
    }

    /**
     * 공개 상태와 발행일 조건을 가진 게시글-태그 기본 조회를 만든다.
     *
     * @param postDetail 게시글 상세 Q 타입
     * @param category 카테고리 Q 타입
     * @param series 시리즈 Q 타입
     * @param postTag 게시글-태그 연결 Q 타입
     * @param tag 태그 Q 타입
     * @return 정렬과 추가 조건을 더할 수 있는 Querydsl 쿼리
     */
    private JPAQuery<Tuple> fetchPublishedRows(
            QPostDetail postDetail,
            QPostCategory category,
            QPostSeries series,
            QPostTag postTag,
            QTag tag
    ) {
        return queryFactory
                .select(postDetail.id, postDetail.slug, postDetail.title, postDetail.summary,
                        postDetail.content, postDetail.publishedAt, tag.name, postDetail.seriesOrder,
                        QAttachedFile.attachedFile.storedName, category.name, series.slug, series.name)
                .from(postDetail)
                .leftJoin(QAttachedFile.attachedFile).on(QAttachedFile.attachedFile.id.eq(postDetail.representativeImageId))
                .leftJoin(category).on(category.id.eq(postDetail.categoryId))
                .leftJoin(series).on(series.id.eq(postDetail.seriesId),
                        series.status.in("ACTIVE", "COMPLETED"), series.postId.eq(postDetail.postId))
                .leftJoin(postTag).on(postTag.id.postDetailId.eq(postDetail.id))
                .leftJoin(tag).on(tag.id.eq(postTag.id.tagId))
                .where(postDetail.status.eq(PUBLISHED), postDetail.publishedAt.loe(java.time.Instant.now()));
    }

    /**
     * 카테고리 슬러그가 유효할 때만 Querydsl 조건식을 만든다.
     *
     * @param category 카테고리 Q 타입
     * @param categorySlug 카테고리 슬러그
     * @return 적용할 조건식 또는 조건을 생략하기 위한 null
     */
    private BooleanExpression categorySlugMatches(QPostCategory category, String categorySlug) {
        if (categorySlug == null || categorySlug.isBlank()) {
            return null;
        }
        return category.slug.eq(categorySlug);
    }

    /**
     * 검색어가 있으면 제목, 요약, 본문에 대한 대소문자 무시 포함 조건을 만든다.
     *
     * @param postDetail 게시글 상세 Q 타입
     * @param search 검색어
     * @return 검색 조건식 또는 조건을 생략하기 위한 null
     */
    private BooleanExpression searchMatches(QPostDetail postDetail, String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = search.strip();
        return postDetail.title.containsIgnoreCase(term)
                .or(postDetail.summary.containsIgnoreCase(term))
                .or(postDetail.content.containsIgnoreCase(term));
    }

    /**
     * Querydsl Tuple 값을 공개 게시글 조회 행으로 옮긴다.
     *
     * @param row 조회 결과 Tuple
     * @param postDetail 게시글 상세 Q 타입
     * @param tag 태그 Q 타입
     * @return 화면 변환에 사용할 공개 게시글 행
     */
    private PublicPostRow toPublicPostRow(Tuple row, QPostDetail postDetail, QTag tag) {
        return new PublicPostRow(
                row.get(postDetail.id),
                row.get(postDetail.slug),
                row.get(postDetail.title),
                row.get(postDetail.summary),
                row.get(postDetail.content),
                row.get(postDetail.publishedAt),
                row.get(tag.name),
                row.get(postDetail.seriesOrder),
                row.get(QAttachedFile.attachedFile.storedName),
                row.get(QPostCategory.postCategory.name),
                row.get(QPostSeries.postSeries.slug), row.get(QPostSeries.postSeries.name)
        );
    }
}
