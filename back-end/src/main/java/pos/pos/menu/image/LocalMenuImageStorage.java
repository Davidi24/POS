package pos.pos.menu.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

// Development only: dish photos in a folder next to the backend, served by /public/menu-images/**.
@Service
@ConditionalOnProperty(name = "app.storage.menu-images.provider", havingValue = "local")
public class LocalMenuImageStorage implements MenuImageStorage {

    private final Path root;

    public LocalMenuImageStorage(@Value("${app.storage.menu-images.local-directory:uploads}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID restaurantId, byte[] content, String contentType, String extension) {
        String key = MenuImageStorage.newKey(restaurantId, extension);
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not save the photo. Try again.", exception);
        }
        return key;
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete " + objectKey, exception);
        }
    }

    @Override
    public Optional<StoredPhoto> load(String objectKey) {
        Path file = resolve(objectKey);
        if (!Files.isReadable(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredPhoto(Files.readAllBytes(file), MenuImageUploads.contentTypeOf(objectKey)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public String urlFor(String objectKey) {
        return MenuImageStorage.backendUrl(objectKey);
    }

    @Override
    public Optional<String> keyOf(String url) {
        return MenuImageStorage.keyFromBackendUrl(url);
    }

    private Path resolve(String key) {
        if (!MenuImageStorage.isValidKey(key)) {
            throw new IllegalArgumentException("Not a menu photo key");
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Not a menu photo key");
        }
        return file;
    }
}
