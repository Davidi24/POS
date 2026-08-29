package pos.pos.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FloorPlanImageController {

    private final FloorPlanImageStorage storage;

    @GetMapping("/public/floor-plan-images/{restaurantId}/{branchId}/{fileName}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable String fileName
    ) {
        Resource resource = storage.load(restaurantId, branchId, fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(mediaType(fileName))
                .body(resource);
    }

    private MediaType mediaType(String fileName) {
        if (fileName.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (fileName.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
