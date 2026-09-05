package com.deanp.blog.post.persistence.query;

import com.deanp.blog.post.persistence.entity.QPostCategory;
import com.deanp.blog.post.persistence.entity.QPostDetail;
import com.deanp.blog.tag.persistence.entity.QPostTag;
import com.deanp.blog.tag.persistence.entity.QTag;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;

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
        // Querydsl Q 타입을 준비해 게시글, 카테고리, 태그 조인 대상을 명확히 한다.
        QPostDetail postDetail = QPostDetail.postDetail;
        QPostCategory category = QPostCategory.postCategory;
        QPostTag postTag = QPostTag.postTag;
        QTag tag = QTag.tag;

        // 공개 글 기본 쿼리에 카테고리 슬러그 조건이 있을 때만 추가한다.
        JPAQuery<Tuple> query = fetchPublishedRows(postDetail, category, postTag, tag);
        BooleanExpression categoryFilter = categorySlugMatches(category, categorySlug);
        if (categoryFilter != null) {
            query.where(categoryFilter);
        }

        // 정렬된 쿼리 결과를 공개 화면용 행 객체로 변환한다.
        return query.orderBy(postDetail.publishedAt.desc(), postDetail.id.asc(), tag.name.asc())
                .fetch()
                .stream()
                .map(row -> toPublicPostRow(row, postDetail, tag))
                .toList();
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
        QPostTag postTag = QPostTag.postTag;
        QTag tag = QTag.tag;

        // 공개 글 기본 쿼리에 슬러그 조건을 더하고 태그명 순으로 결과를 안정화한다.
        return fetchPublishedRows(postDetail, category, postTag, tag)
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
     * @param postTag 게시글-태그 연결 Q 타입
     * @param tag 태그 Q 타입
     * @return 정렬과 추가 조건을 더할 수 있는 Querydsl 쿼리
     */
    private JPAQuery<Tuple> fetchPublishedRows(
            QPostDetail postDetail,
            QPostCategory category,
            QPostTag postTag,
            QTag tag
    ) {
        return queryFactory
                .select(postDetail.id, postDetail.slug, postDetail.title, postDetail.summary,
                        postDetail.content, postDetail.publishedAt, tag.name)
                .from(postDetail)
                .leftJoin(category).on(category.id.eq(postDetail.categoryId))
                .leftJoin(postTag).on(postTag.id.postDetailId.eq(postDetail.id))
                .leftJoin(tag).on(tag.id.eq(postTag.id.tagId))
                .where(postDetail.status.eq(PUBLISHED), postDetail.publishedAt.isNotNull());
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
                row.get(tag.name)
        );
    }
}
