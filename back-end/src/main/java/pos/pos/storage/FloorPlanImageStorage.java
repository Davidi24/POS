package pos.pos.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FloorPlanImageStorage {

    StoredImage store(UUID restaurantId, UUID branchId, MultipartFile file);

    Resource load(UUID restaurantId, UUID branchId, String fileName);

    void delete(UUID restaurantId, UUID branchId, String fileName);

    record StoredImage(String objectKey, String url, String contentType, long size) {
    }
}
