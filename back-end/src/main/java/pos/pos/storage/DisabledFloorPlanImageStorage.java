package pos.pos.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.floor-plans.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledFloorPlanImageStorage implements FloorPlanImageStorage {

    @Override
    public StoredImage store(UUID restaurantId, UUID branchId, MultipartFile file) {
        throw unavailable();
    }

    @Override
    public Resource load(UUID restaurantId, UUID branchId, String fileName) {
        throw unavailable();
    }

    @Override
    public void delete(UUID restaurantId, UUID branchId, String fileName) {
        // No provider is active, so there is no backing object to remove.
    }

    @Override
    public String publicUrl(String objectKey) {
        return null;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Floor-plan image storage is not configured");
    }
}
