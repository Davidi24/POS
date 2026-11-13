package pos.pos.restaurant.controller;

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
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.service.RestaurantLogoService;

import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/{restaurantId}/branding/logo")
@RequiredArgsConstructor
public class RestaurantLogoController {

    private final RestaurantLogoService restaurantLogoService;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Upload the restaurant's logo (PNG, JPEG or WebP, up to 5 MB); replaces the earlier one")
    public ResponseEntity<RestaurantBrandingResponse> uploadLogo(
            @PathVariable UUID restaurantId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantLogoService.uploadLogo(authentication, restaurantId, file));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Remove the restaurant's logo")
    public ResponseEntity<RestaurantBrandingResponse> removeLogo(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(restaurantLogoService.removeLogo(authentication, restaurantId));
    }
}
