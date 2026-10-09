package pos.pos.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.floor-plans.provider", havingValue = "local")
public class LocalFloorPlanImageStorage implements FloorPlanImageStorage {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp"
    );

    private final Path root;

    public LocalFloorPlanImageStorage(
            @Value("${app.storage.floor-plans.local-directory:uploads/floor-plans}") String directory
    ) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public StoredImage store(UUID restaurantId, UUID branchId, MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read floor-plan image", exception);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must not exceed 10 MB");
        }
        if (!matchesSignature(contentType, content)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image content does not match its declared type");
        }

        String fileName = UUID.randomUUID() + EXTENSIONS.get(contentType);
        Path directory = scopedDirectory(restaurantId, branchId);
        Path destination = resolveFile(directory, fileName);

        try {
            Files.createDirectories(directory);
            Files.write(destination, content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store floor-plan image", exception);
        }

        String objectKey = restaurantId + "/" + branchId + "/" + fileName;
        String url = "/public/floor-plan-images/" + objectKey;
        return new StoredImage(objectKey, url, contentType, content.length);
    }

    @Override
    public Resource load(UUID restaurantId, UUID branchId, String fileName) {
        Path file = resolveFile(scopedDirectory(restaurantId, branchId), fileName);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor-plan image not found");
            }
            return resource;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor-plan image not found", exception);
        }
    }

    @Override
    public void delete(UUID restaurantId, UUID branchId, String fileName) {
        Path file = resolveFile(scopedDirectory(restaurantId, branchId), fileName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete floor-plan image", exception);
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return "/public/floor-plan-images/" + objectKey;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must not exceed 10 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !EXTENSIONS.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG, and WebP images are supported");
        }
    }

    private boolean matchesSignature(String contentType, byte[] content) {
        if ("image/png".equals(contentType)) {
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            return startsWith(content, signature, 0);
        }
        if ("image/jpeg".equals(contentType)) {
            return content.length >= 3
                    && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8
                    && (content[2] & 0xff) == 0xff;
        }
        if ("image/webp".equals(contentType)) {
            return content.length >= 12
                    && startsWith(content, new byte[]{'R', 'I', 'F', 'F'}, 0)
                    && startsWith(content, new byte[]{'W', 'E', 'B', 'P'}, 8);
        }
        return false;
    }

    private boolean startsWith(byte[] content, byte[] prefix, int offset) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[offset + index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private Path scopedDirectory(UUID restaurantId, UUID branchId) {
        Path directory = root.resolve(restaurantId.toString()).resolve(branchId.toString()).normalize();
        if (!directory.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage path");
        }
        return directory;
    }

    private Path resolveFile(Path directory, String fileName) {
        if (fileName == null || !fileName.matches("[0-9a-fA-F-]{36}\\.(png|jpg|webp)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file name");
        }
        Path file = directory.resolve(fileName).normalize();
        if (!file.startsWith(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage path");
        }
        return file;
    }
}
