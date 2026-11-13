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
import pos.pos.inventory.dto.InventoryItemResponse;
import pos.pos.inventory.service.InventoryItemPhotoService;

import java.util.UUID;

@Tag(name = "Inventory Items")
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/items/{itemId}/image")
@RequiredArgsConstructor
public class InventoryItemPhotoController {

    private final InventoryItemPhotoService photoService;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "The stock item's photo (PNG, JPEG or WebP, up to 5 MB); replaces the old one")
    public ResponseEntity<InventoryItemResponse> uploadPhoto(@PathVariable UUID restaurantId, @PathVariable UUID itemId,
                                                             @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.ok(photoService.uploadPhoto(authentication, restaurantId, itemId, file));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Remove the stock item's photo")
    public ResponseEntity<InventoryItemResponse> removePhoto(@PathVariable UUID restaurantId, @PathVariable UUID itemId,
                                                             Authentication authentication) {
        return ResponseEntity.ok(photoService.removePhoto(authentication, restaurantId, itemId));
    }
}
