package pos.pos.menu.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

// No photo storage configured: uploads are refused with a clear message instead of being lost.
@Service
@ConditionalOnProperty(name = "app.storage.menu-images.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledMenuImageStorage implements MenuImageStorage {

    @Override
    public String store(UUID restaurantId, byte[] content, String contentType, String extension) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Photo uploads aren't set up on this server yet");
    }

    @Override
    public void delete(String objectKey) {
    }

    @Override
    public Optional<StoredPhoto> load(String objectKey) {
        return Optional.empty();
    }

    @Override
    public String urlFor(String objectKey) {
        return MenuImageStorage.backendUrl(objectKey);
    }

    @Override
    public Optional<String> keyOf(String url) {
        return MenuImageStorage.keyFromBackendUrl(url);
    }
}
