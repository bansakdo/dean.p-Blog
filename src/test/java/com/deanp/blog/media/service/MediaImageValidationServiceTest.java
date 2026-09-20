package com.deanp.blog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MediaImageValidationServiceTest {

    private final MediaImageValidationService service = new MediaImageValidationService();

    @Test
    void 정상적인_png_이미지를_검증한다() throws Exception {
        byte[] content = pngBytes(120, 80);
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "sample.png",
                "image/png",
                content
        );

        MediaImageValidationService.ValidatedImage result = service.validate(file);

        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.fileSize()).isEqualTo(content.length);
        assertThat(result.width()).isEqualTo(120);
        assertThat(result.height()).isEqualTo(80);
    }

    @Test
    void 확장자와_MIME_타입이_다르면_거부한다() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "sample.png",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class)
                .hasMessageContaining("MIME");
    }

    @Test
    void 이미지가_아닌_파일은_거부한다() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "sample.png",
                "image/png",
                "not-an-image".getBytes()
        );

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class)
                .hasMessageContaining("읽을 수 없는");
    }

    @Test
    void 경로가_포함된_파일명은_거부한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "../sample.png",
                "image/png",
                pngBytes(20, 20)
        );

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class)
                .hasMessageContaining("파일명");
    }

    @Test
    void 너무_큰_이미지는_거부한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "large.png",
                "image/png",
                pngBytes(8_001, 1)
        );

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(MediaImageValidationService.MediaValidationException.class)
                .hasMessageContaining("해상도");
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
