package com.deanp.blog.media.controller;

import com.deanp.blog.media.service.PostDiagramService;
import com.deanp.blog.media.service.PostDiagramService.DiagramFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 게시글에 첨부한 Archify 다이어그램 HTML을 격리 헤더와 함께 내보낸다.
 * CSP sandbox로 블로그와 다른 출처(opaque origin)에서 실행되므로, 주소를 직접 열어도 블로그 쿠키·화면에 접근하지 못한다.
 */
@RestController
@RequiredArgsConstructor
public class DiagramController {

    /**
     * 다이어그램이 쓰는 인라인 스크립트·스타일과 data:/blob: 폰트·이미지·영상만 허용하고, 네트워크 요청과 폼 전송은 막는다.
     * 같은 블로그 화면 안의 iframe에만 넣을 수 있다.
     */
    static final String CONTENT_SECURITY_POLICY = "sandbox allow-scripts allow-downloads allow-popups; "
            + "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
            + "img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; form-action 'none'; "
            + "base-uri 'none'; frame-ancestors 'self'";

    private final PostDiagramService diagrams;

    /**
     * 다이어그램 파일을 흘려보낸다. 업로드마다 새 식별자를 쓰므로 내용이 바뀌지 않아 오래 캐시하고,
     * 체크섬을 ETag로 붙여 다시 요청하면 304로 답한다.
     *
     * @param id 다이어그램 첨부 파일 식별자
     * @return 격리 헤더를 붙인 다이어그램 HTML, 없으면 404
     */
    @GetMapping("/media/diagrams/{id}")
    public ResponseEntity<Resource> diagram(@PathVariable UUID id) {
        DiagramFile file = diagrams.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Frame-Options", "SAMEORIGIN")
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer");
        if (file.checksum() != null) {
            response.eTag("\"" + file.checksum().strip() + "\"");
        }
        return response.body(new FileSystemResource(file.path()));
    }
}
