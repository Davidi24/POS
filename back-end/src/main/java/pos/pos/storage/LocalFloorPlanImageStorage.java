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
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.floor-plans.provider", havingValue = "local", matchIfMissing = true)
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
        String fileName = UUID.randomUUID() + EXTENSIONS.get(contentType);
        Path directory = scopedDirectory(restaurantId, branchId);
        Path destination = resolveFile(directory, fileName);

        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store floor-plan image", exception);
        }

        String objectKey = restaurantId + "/" + branchId + "/" + fileName;
        String url = "/public/floor-plan-images/" + objectKey;
        return new StoredImage(objectKey, url, contentType, file.getSize());
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
