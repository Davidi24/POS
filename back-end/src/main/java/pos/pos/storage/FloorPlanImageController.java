package pos.pos.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FloorPlanImageController {

    private final FloorPlanImageStorage storage;
    private final RestaurantScopeService restaurantScopeService;

    @PostMapping(
            path = "/restaurants/{restaurantId}/branches/{branchId}/floor-plan-images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ResponseEntity<FloorPlanImageResponse> upload(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FloorPlanImageResponse.from(storage.store(restaurantId, branchId, file)));
    }

    @DeleteMapping("/restaurants/{restaurantId}/branches/{branchId}/floor-plan-images/{fileName}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable String fileName,
            Authentication authentication
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        storage.delete(restaurantId, branchId, fileName);
        return ResponseEntity.noContent().build();
    }

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
