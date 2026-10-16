package com.deanp.blog.publishing.service;

import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import com.deanp.blog.publishing.persistence.entity.PostRevision;
import com.deanp.blog.publishing.persistence.repository.PostRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 게시글의 제목·요약·본문 변경 이력을 남기고, 이력 조회와 이전 버전으로의 되돌림을 트랜잭션으로 조합한다.
 */
@Service
@RequiredArgsConstructor
public class PostRevisionService {

    private static final String DELETED = "DELETED";
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final PostRevisionRepository revisions;
    private final PostDetailRepository posts;
    private final AppUserRepository users;

    /**
     * 이력 기능 전에 만든 글처럼 리비전이 하나도 없으면 바꾸기 전 상태를 첫 버전으로 남긴다.
     * 글을 고치기 전에, 잠근 글로 호출해야 한다.
     *
     * @param post 아직 고치지 않은 게시글
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBaselineIfMissing(PostDetail post) {
        if (revisions.findTopByPostDetailIdOrderByRevisionNoDesc(post.getId()).isEmpty()) {
            revisions.save(PostRevision.create(post.getId(), 1, post.getTitle(), post.getSummary(), post.getContent(),
                    post.getContentFormat(), null, RevisionChangeType.BASELINE.name(), post.getUpdatedAt()));
        }
    }

    /**
     * 게시글의 현재 제목·요약·본문을 새 버전으로 남긴다. 저장과 되돌림은
     * 직전 버전과 내용이 같으면 남기지 않는다. 같은 글을 동시에 기록하지 않도록 잠근 글로 호출해야 한다.
     *
     * @param post 변경을 마친 게시글
     * @param type 변경 종류
     * @param loginId 변경한 사용자의 로그인 아이디
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(PostDetail post, RevisionChangeType type, String loginId) {
        Optional<PostRevision> latest = revisions.findTopByPostDetailIdOrderByRevisionNoDesc(post.getId());
        boolean contentOnly = type == RevisionChangeType.UPDATE || type == RevisionChangeType.REVERT;
        if (contentOnly && latest.filter(revision -> sameContent(revision, post)).isPresent()) {
            return;
        }
        int revisionNo = latest.map(revision -> revision.getRevisionNo() + 1).orElse(1);
        UUID changedBy = users.findByLoginId(loginId).map(user -> user.getId()).orElse(null);
        revisions.save(PostRevision.create(post.getId(), revisionNo, post.getTitle(), post.getSummary(),
                post.getContent(), post.getContentFormat(), changedBy, type.name(), Instant.now()));
    }

    /**
     * @param postId 게시글 식별자
     * @return 게시글의 변경 이력, 최신 버전부터
     */
    @Transactional(readOnly = true)
    public List<RevisionRow> findHistory(UUID postId) {
        return revisions.findSummariesByPostDetailId(postId).stream()
                .map(revision -> new RevisionRow(revision.revisionNo(), RevisionChangeType.labelOf(revision.changeType()),
                        LocalDateTime.ofInstant(revision.createdAt(), DISPLAY_ZONE), revision.changedBy()))
                .toList();
    }

    /**
     * 게시글의 한 버전과, 그 버전이 현재 글과 어떤 항목에서 다른지 조회한다.
     *
     * @param postId 게시글 식별자
     * @param revisionNo 버전 번호
     * @return 버전 내용, 게시글이나 버전이 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<RevisionView> find(UUID postId, int revisionNo) {
        Optional<PostDetail> post = posts.findById(postId);
        return post.flatMap(current -> revisions.findByPostDetailIdAndRevisionNo(postId, revisionNo)
                .map(revision -> new RevisionView(revision.getRevisionNo(),
                        RevisionChangeType.labelOf(revision.getChangeType()),
                        LocalDateTime.ofInstant(revision.getCreatedAt(), DISPLAY_ZONE), changedByName(revision),
                        revision.getTitle(), revision.getSummary(), revision.getContent(),
                        !Objects.equals(revision.getTitle(), current.getTitle()),
                        !Objects.equals(revision.getSummary(), current.getSummary()),
                        !Objects.equals(revision.getContent(), current.getContent()))));
    }

    /**
     * 게시글의 제목·요약·본문을 지정한 버전으로 되돌리고 그 결과를 새 버전으로 남긴다.
     * 주소·분류·태그·공개 상태는 그대로 두므로 공개 중인 글은 바로 공개 화면에 반영된다.
     * 현재 내용과 같은 버전이면 새 버전을 남기지 않는다.
     *
     * @param postId 게시글 식별자
     * @param revisionNo 되돌릴 버전 번호
     * @param loginId 되돌린 사용자의 로그인 아이디
     */
    @Transactional
    public void revert(UUID postId, int revisionNo, String loginId) {
        PostDetail post = posts.findForUpdateById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        if (DELETED.equals(post.getStatus())) {
            throw new IllegalArgumentException("삭제한 글은 복구한 뒤에 되돌릴 수 있습니다.");
        }
        PostRevision revision = revisions.findByPostDetailIdAndRevisionNo(postId, revisionNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 버전입니다."));
        post.edit(post.getSlug(), revision.getTitle(), revision.getSummary(), revision.getContent());
        record(post, RevisionChangeType.REVERT, loginId);
    }

    /**
     * @param revision 비교할 리비전
     * @param post 비교할 게시글
     * @return 제목·요약·본문이 모두 같은지 여부
     */
    private static boolean sameContent(PostRevision revision, PostDetail post) {
        return Objects.equals(revision.getTitle(), post.getTitle())
                && Objects.equals(revision.getSummary(), post.getSummary())
                && Objects.equals(revision.getContent(), post.getContent());
    }

    /**
     * @param revision 리비전
     * @return 변경한 사용자의 로그인 아이디, 기록이 없으면 null
     */
    private static String changedByName(PostRevision revision) {
        return revision.getChangedByUser() == null ? null : revision.getChangedByUser().getLoginId();
    }

    /**
     * 변경 이력 목록의 한 줄이다.
     *
     * @param revisionNo 버전 번호
     * @param changeType 화면에 보일 변경 종류
     * @param createdAt 화면 표시 시간대로 바꾼 기록 시각
     * @param changedBy 변경한 사용자의 로그인 아이디 또는 null
     */
    public record RevisionRow(int revisionNo, String changeType, LocalDateTime createdAt, String changedBy) {
    }

    /**
     * 한 버전의 내용과 현재 글과의 차이다.
     *
     * @param revisionNo 버전 번호
     * @param changeType 화면에 보일 변경 종류
     * @param createdAt 화면 표시 시간대로 바꾼 기록 시각
     * @param changedBy 변경한 사용자의 로그인 아이디 또는 null
     * @param title 제목
     * @param summary 요약 또는 null
     * @param content Markdown 본문
     * @param titleDiffers 현재 글과 제목이 다른지 여부
     * @param summaryDiffers 현재 글과 요약이 다른지 여부
     * @param contentDiffers 현재 글과 본문이 다른지 여부
     */
    public record RevisionView(int revisionNo, String changeType, LocalDateTime createdAt, String changedBy,
                               String title, String summary, String content,
                               boolean titleDiffers, boolean summaryDiffers, boolean contentDiffers) {
    }
}
