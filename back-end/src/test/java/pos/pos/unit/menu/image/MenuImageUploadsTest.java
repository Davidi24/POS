package pos.pos.unit.menu.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.menu.image.MenuImageUploads;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuImageUploadsTest {

    @Test
    void acceptsFullyDecodableSupportedFormats() throws IOException {
        for (String format : new String[]{"png", "jpeg", "webp"}) {
            byte[] content = encode(format);
            String mime = format.equals("jpeg") ? "image/jpeg" : "image/" + format;
            var checked = MenuImageUploads.check(new MockMultipartFile("file", "dish." + format, mime, content));
            assertEquals(content.length, checked.content().length);
        }
    }

    @Test
    void rejectsOnlyPngSignature() {
        byte[] signatureOnly = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThrows(ResponseStatusException.class, () -> MenuImageUploads.check(
                new MockMultipartFile("file", "dish.png", "image/png", signatureOnly)));
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchDecodedFormat() throws IOException {
        assertThrows(ResponseStatusException.class, () -> MenuImageUploads.check(
                new MockMultipartFile("file", "dish.jpg", "image/jpeg", encode("png"))));
    }

    @Test
    void rejectsImagesOverPixelLimit() throws IOException {
        BufferedImage large = new BufferedImage(7000, 7000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(large, "png", bytes);
        assertThrows(ResponseStatusException.class, () -> MenuImageUploads.check(
                new MockMultipartFile("file", "dish.png", "image/png", bytes.toByteArray())));
    }

    private static byte[] encode(String format) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, bytes)) throw new IllegalStateException("No ImageIO writer for " + format);
        return bytes.toByteArray();
    }
}
