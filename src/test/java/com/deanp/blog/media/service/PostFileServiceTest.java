package com.deanp.blog.media.service;

import com.deanp.blog.auth.persistence.entity.AppUser;
import com.deanp.blog.auth.persistence.repository.AppUserRepository;
import com.deanp.blog.media.persistence.entity.AttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFile;
import com.deanp.blog.media.persistence.entity.PostAttachedFileId;
import com.deanp.blog.media.persistence.repository.AttachedFileRepository;
import com.deanp.blog.media.persistence.repository.PostAttachedFileRepository;
import com.deanp.blog.media.service.MediaImageValidationService.MediaValidationException;
import com.deanp.blog.post.persistence.entity.PostDetail;
import com.deanp.blog.post.persistence.repository.PostDetailRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 첨부 파일의 허용 형식·크기 검증, 저장 위치, 공개 상태와 작성자에 따른 다운로드 범위, 삭제를 확인한다. */
class PostFileServiceTest {

    private static final UUID POST_ID = new UUID(0, 7);
    private static final UUID AUTHOR_ID = new UUID(0, 1);
    private static final UUID FILE_ID = new UUID(0, 21);

    @TempDir
    Path root;

    private final AttachedFileRepository attachedFiles = mock(AttachedFileRepository.class);
    private final PostAttachedFileRepository postAttachedFiles = mock(PostAttachedFileRepository.class);
    private final PostDetailRepository posts = mock(PostDetailRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private PostFileService service;

    /** 실제 임시 저장소, 있다고 답하는 게시글, 작성자 admin과 다른 사용자 guest를 준비한다. */
    @BeforeEach
    void setUp() {
        service = new PostFileService(new MediaStorageService(root), attachedFiles, postAttachedFiles, posts, users);
        when(posts.existsById(POST_ID)).thenReturn(true);
        when(attachedFiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // 목 안에서 다른 목을 꾸미면 Mockito 스터빙이 꼬이므로 사용자를 먼저 만든다.
        AppUser author = user(AUTHOR_ID);
        AppUser guest = user(new UUID(0, 2));
        when(users.findByLoginId("admin")).thenReturn(Optional.of(author));
        when(users.findByLoginId("guest")).thenReturn(Optional.of(guest));
    }

    /** 테스트가 연 트랜잭션 동기화를 정리한다. */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 허용한 파일은 공개 정적 경로(posts) 밖 files 영역에 확장자로 정한 MIME 타입과 체크섬으로 기록하고, 본문 링크를 돌려준다. */
    @Test
    void storesAllowedFileOutsidePublicPostsArea() throws Exception {
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        var file = service.upload(POST_ID, new MockMultipartFile("file", "C:\\fakepath\\발표 [최종].pdf", "text/html", content));

        ArgumentCaptor<AttachedFile> saved = ArgumentCaptor.forClass(AttachedFile.class);
        verify(attachedFiles).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOriginalName()).isEqualTo("발표 [최종].pdf");
        assertThat(saved.getValue().getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getValue().getStoragePath()).isEqualTo("files/" + POST_ID + "/" + file.id() + ".pdf");
        assertThat(saved.getValue().getChecksum()).isEqualTo(HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(content)));
        assertThat(Files.readAllBytes(root.resolve(saved.getValue().getStoragePath()))).isEqualTo(content);
        assertThat(Files.exists(root.resolve("posts"))).isFalse();
        assertThat(file.markdown()).isEqualTo("[발표 \\[최종\\].pdf](/media/files/" + file.id() + ")");
        assertThat(file.displaySize()).isEqualTo("8 B");
    }

    /** 허용하지 않은 확장자, 빈 파일, 20MB 초과 파일은 저장하지 않는다. */
    @Test
    void rejectsDisallowedOrOversizedFiles() {
        for (String name : new String[] {"setup.exe", "page.html", "photo.png", "README"}) {
            assertThatThrownBy(() -> service.upload(POST_ID, new MockMultipartFile("file", name, null, new byte[] {1})))
                    .isInstanceOf(MediaValidationException.class).hasMessageContaining("올릴 수 없는 파일 형식");
        }
        assertThatThrownBy(() -> service.upload(POST_ID, new MockMultipartFile("file", "empty.txt", null, new byte[0])))
                .isInstanceOf(MediaValidationException.class).hasMessageContaining("빈 파일");
        byte[] large = new byte[(int) PostFileService.MAX_SIZE + 1];
        assertThatThrownBy(() -> service.upload(POST_ID, new MockMultipartFile("file", "big.zip", null, large)))
                .isInstanceOf(MediaValidationException.class).hasMessageContaining("20MB");

        verify(attachedFiles, never()).saveAndFlush(any());
        assertThat(root.toFile().list()).isEmpty();
    }

    /** 글자 방향을 바꾸는 보이지 않는 문자는 지워 확장자를 속이지 못하게 하고, 링크 글자의 &도 이스케이프한다. */
    @Test
    void stripsDirectionOverridesAndEscapesAmpersand() {
        var file = service.upload(POST_ID, new MockMultipartFile("file", "a&b\u202Efdp.sh", null, new byte[] {1}));

        assertThat(file.name()).isEqualTo("a&bfdp.sh");
        assertThat(file.markdown()).startsWith("[a\\&bfdp.sh](");
    }

    /** 공개 글의 파일은 누구나 받는다. */
    @Test
    void anyoneDownloadsFileOfPublicPost() throws Exception {
        PostDetail post = post("PUBLISHED", Instant.now().minusSeconds(60));
        stubLink(post, "files");

        assertThat(service.findForDownload(FILE_ID, null)).hasValueSatisfying(file -> {
            assertThat(file.name()).isEqualTo("slides.pdf");
            assertThat(file.contentType()).isEqualTo("application/pdf");
        });
    }

    /** 초안·예약·삭제한 글의 파일은 로그인한 작성자만 받고, 로그인하지 않았거나 다른 사용자이면 받지 못한다. */
    @Test
    void onlyAuthorDownloadsFileOfNonPublicPost() throws Exception {
        for (PostDetail post : new PostDetail[] {
                post("DRAFT", null),
                post("PUBLISHED", Instant.now().plusSeconds(3600)),
                post("DELETED", Instant.now().minusSeconds(60))}) {
            stubLink(post, "files");

            assertThat(service.findForDownload(FILE_ID, null)).isEmpty();
            assertThat(service.findForDownload(FILE_ID, "guest")).isEmpty();
            assertThat(service.findForDownload(FILE_ID, "unknown")).isEmpty();
            assertThat(service.findForDownload(FILE_ID, "admin")).isPresent();
        }
    }

    /** 이미지·다이어그램은 첨부 파일 경로로 내려받을 수 없다. */
    @Test
    void doesNotServeImagesOrDiagramsAsFiles() throws Exception {
        PostDetail post = post("PUBLISHED", Instant.now().minusSeconds(60));
        stubLink(post, "diagrams");

        assertThat(service.findForDownload(FILE_ID, "admin")).isEmpty();
    }

    /** 삭제는 연결과 메타데이터를 지우고, 트랜잭션이 끝나면 저장한 파일을 지운다. 다른 글의 파일은 지우지 않는다. */
    @Test
    void deletesLinkMetadataAndFileAfterCommit() throws Exception {
        PostDetail post = post("DRAFT", null);
        PostAttachedFile link = stubLink(post, "files");
        when(postAttachedFiles.findById(new PostAttachedFileId(POST_ID, FILE_ID))).thenReturn(Optional.of(link));
        TransactionSynchronizationManager.initSynchronization();

        assertThat(service.delete(new UUID(0, 99), FILE_ID)).isFalse();
        assertThat(service.delete(POST_ID, FILE_ID)).isTrue();
        verify(postAttachedFiles).delete(link);
        verify(attachedFiles).deleteById(FILE_ID);
        Path stored = root.resolve("files/" + POST_ID + "/" + FILE_ID + ".pdf");
        assertThat(stored).exists();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(stored).doesNotExist();
    }

    /**
     * 첨부 파일 하나가 주어진 글에 연결돼 있고 저장소에 실제 파일이 있다고 꾸민다.
     *
     * @param post 파일이 연결된 글
     * @param area 파일이 저장된 영역
     * @return 꾸민 첨부 연결
     */
    private PostAttachedFile stubLink(PostDetail post, String area) throws Exception {
        String storagePath = area + "/" + POST_ID + "/" + FILE_ID + ".pdf";
        Files.createDirectories(root.resolve(storagePath).getParent());
        Files.writeString(root.resolve(storagePath), "%PDF");
        AttachedFile file = AttachedFile.create(FILE_ID, "slides.pdf", FILE_ID + ".pdf", storagePath,
                "application/pdf", 4, null);
        PostAttachedFile link = mock(PostAttachedFile.class);
        when(link.getAttachedFile()).thenReturn(file);
        when(link.getPostDetail()).thenReturn(post);
        when(postAttachedFiles.findFirstByIdAttachedFileId(FILE_ID)).thenReturn(Optional.of(link));
        return link;
    }

    /**
     * @param status 글 상태
     * @param publishedAt 발행 시각 또는 null
     * @return admin이 쓴 글
     */
    private static PostDetail post(String status, Instant publishedAt) {
        PostDetail post = mock(PostDetail.class);
        when(post.getStatus()).thenReturn(status);
        when(post.getPublishedAt()).thenReturn(publishedAt);
        when(post.getAuthorId()).thenReturn(AUTHOR_ID);
        return post;
    }

    /**
     * @param id 사용자 식별자
     * @return 식별자만 답하는 사용자
     */
    private static AppUser user(UUID id) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        return user;
    }
}
