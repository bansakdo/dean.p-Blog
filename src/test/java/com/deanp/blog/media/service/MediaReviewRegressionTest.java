package com.deanp.blog.media.service;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** 이미지 보안 리뷰에서 발견된 오류의 재발을 검증한다. */
class MediaReviewRegressionTest {
    @TempDir Path root;
    private final MediaImageValidationService validator = new MediaImageValidationService();

    /** 실제 이미지 형식과 선언한 MIME이 다르면 거부한다. */
    @Test void rejectsDisguisedPng() throws Exception {
        assertThatThrownBy(() -> validator.validate(file("fake.jpg", "image/jpeg", image("png",2,2))))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class).hasMessageContaining("실제");
    }
    /** JPEG와 WebP를 실제로 디코딩한다. */
    @Test void acceptsJpegAndWebp() throws Exception {
        assertThat(validator.validate(file("ok.jpg","image/jpeg",image("jpeg",2,2))).width()).isEqualTo(2);
        try(var input=getClass().getResourceAsStream("/media/valid.webp")) {
            assertThat(input).isNotNull();
            assertThat(validator.validate(file("ok.webp","image/webp",input.readAllBytes())).width()).isEqualTo(2);
        }
    }
    /** 깨진 이미지 데이터를 거부한다. */
    @Test void rejectsTruncatedData() throws Exception {
        byte[] bytes=image("png",20,20);
        assertThatThrownBy(() -> validator.validate(file("bad.png","image/png",Arrays.copyOf(bytes,40))))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class);
    }
    /** 헤더만 있는 과대 이미지는 디코딩 오류보다 크기 제한으로 거부한다. */
    @Test void rejectsDimensionsBeforeDecoding() throws Exception {
        byte[] bytes=Arrays.copyOf(image("png",2,2),33);
        java.nio.ByteBuffer.wrap(bytes).putInt(16,8001);
        assertThatThrownBy(() -> validator.validate(file("large.png","image/png",bytes)))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class).hasMessageContaining("해상도");
        java.nio.ByteBuffer.wrap(bytes).putInt(16,5000).putInt(20,5000);
        assertThatThrownBy(() -> validator.validate(file("large.png","image/png",bytes))).hasMessageContaining("해상도");
    }
    /** 신고된 용량이 작아도 실제 바이트 제한을 적용한다. */
    @Test void rejectsActualOversize() {
        var upload=new MockMultipartFile("file","big.png","image/png",new byte[10*1024*1024+1]) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> validator.validate(upload)).hasMessageContaining("10MB");
    }
    /** 운영체제와 무관하게 위험한 파일명을 거부한다. */
    @Test void rejectsUnsafeNames() throws Exception {
        for(String name:List.of("../a.png","..\\a.png","a\u0000.png","C:a.png")) {
            assertThatThrownBy(() -> validator.validate(file(name,"image/png",image("png",2,2))))
                    .isInstanceOf(MediaImageValidationService.MediaValidationException.class).hasMessageContaining("파일명");
        }
    }
    /** 읽기 실패 시 생성된 파일을 지우고 원래 예외를 유지한다. */
    @Test void cleansFailedCopy() throws Exception {
        var failure=new IOException("read failed");
        var input=new InputStream() {
            int count;
            @Override public int read() throws IOException { if(count++<3)return 1;throw failure; }
        };
        assertThatThrownBy(() -> new MediaStorageService(root).store(UUID.randomUUID(),"png",input)).isSameAs(failure);
        try(var paths=Files.walk(root)) { assertThat(paths.filter(Files::isRegularFile).count()).isZero(); }
    }
    /** 이미지 테스트 파일을 만든다. */
    private MockMultipartFile file(String name,String mime,byte[] bytes) {
        return new MockMultipartFile("file",name,mime,bytes);
    }
    /** 외부 도구 없이 정상 PNG 또는 JPEG 데이터를 생성한다. */
    private byte[] image(String format,int width,int height) throws IOException {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),format,out);
        return out.toByteArray();
    }
}
