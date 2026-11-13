package pos.pos.menu.image;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// Checks a dish photo before it's stored: PNG, JPEG or WebP, at most 5 MB, and the bytes really are that kind of image.
public final class MenuImageUploads {

    public static final long MAX_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_PIXELS = 40_000_000L;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/webp", ".webp"
    );

    private MenuImageUploads() {
    }

    public record CheckedPhoto(byte[] content, String contentType, String extension) {
    }

    public static CheckedPhoto check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a photo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The photo is too big. Use one under 5 MB.");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(declared);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a PNG, JPEG or WebP photo");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the photo", exception);
        }
        if (content.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The photo is too big. Use one under 5 MB.");
        }
        String contentType = ".jpg".equals(extension) ? "image/jpeg" : declared;
        if (!isDecodableImage(contentType, content)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file isn't a valid PNG, JPEG or WebP photo");
        }
        return new CheckedPhoto(content, contentType, extension);
    }

    public static String contentTypeOf(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static boolean isDecodableImage(String expectedContentType, byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean matchesType = switch (expectedContentType) {
                    case "image/png" -> format.equals("png");
                    case "image/jpeg" -> format.equals("jpeg") || format.equals("jpg");
                    case "image/webp" -> format.equals("webp");
                    default -> false;
                };
                if (!matchesType) return false;
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return false;
                return reader.read(0) != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException invalidImage) {
            return false;
        }
    }
}
