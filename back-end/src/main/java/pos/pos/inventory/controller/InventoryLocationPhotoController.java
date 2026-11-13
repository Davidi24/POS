package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.inventory.dto.InventoryLocationResponse;
import pos.pos.inventory.service.InventoryLocationPhotoService;

import java.util.UUID;

@Tag(name = "Inventory Locations")
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/locations/{locationId}/image")
@RequiredArgsConstructor
public class InventoryLocationPhotoController {

    private final InventoryLocationPhotoService photoService;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "The place's own picture (PNG, JPEG or WebP, up to 5 MB); replaces the old one")
    public ResponseEntity<InventoryLocationResponse> uploadPhoto(@PathVariable UUID restaurantId, @PathVariable UUID locationId,
                                                             @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.ok(photoService.uploadPhoto(authentication, restaurantId, locationId, file));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Remove the place's own picture (back to its icon)")
    public ResponseEntity<InventoryLocationResponse> removePhoto(@PathVariable UUID restaurantId, @PathVariable UUID locationId,
                                                             Authentication authentication) {
        return ResponseEntity.ok(photoService.removePhoto(authentication, restaurantId, locationId));
    }
}
