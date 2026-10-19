package com.deanp.blog.media.controller;

import com.deanp.blog.media.service.PostFileService;
import com.deanp.blog.media.service.PostFileService.DownloadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

/**
 * 게시글 첨부 파일을 다운로드로만 내보낸다. 브라우저가 열어 실행하지 않도록 attachment·nosniff·CSP sandbox를 붙인다.
 * 공개 글이 아닌 파일은 로그인한 작성자에게만 주고, 그 밖에는 있는지조차 알리지 않도록 404로 답한다.
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    /** 첨부 파일 응답에 붙이는 CSP. 어떤 이유로 브라우저가 열더라도 스크립트·외부 요청을 막는다. */
    static final String CONTENT_SECURITY_POLICY = "sandbox; default-src 'none'";

    private final PostFileService files;

    /**
     * 첨부 파일을 흘려보낸다. 글의 공개 상태가 바뀔 수 있으므로 공유 캐시에 두지 않고 매번 ETag로 다시 확인한다.
     *
     * @param id 첨부 파일 식별자
     * @param principal 로그인한 사용자, 로그인하지 않았으면 null
     * @return 다운로드 헤더를 붙인 파일, 받을 수 없으면 404
     */
    @GetMapping("/media/files/{id}")
    public ResponseEntity<Resource> file(@PathVariable UUID id, Principal principal) {
        DownloadFile file = files.findForDownload(id, principal == null ? null : principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer");
        if (file.checksum() != null) {
            response.eTag("\"" + file.checksum().strip() + "\"");
        }
        return response.body(new FileSystemResource(file.path()));
    }
}
