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
import pos.pos.restaurant.service.RestaurantCoverService;
import pos.pos.restaurant.service.RestaurantCoverService.CoverResponse;

import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class RestaurantCoverController {

    private final RestaurantCoverService coverService;

    @PutMapping(value = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: the restaurant card's cover picture (PNG, JPEG or WebP, up to 5 MB)")
    public ResponseEntity<CoverResponse> uploadRestaurantCover(@PathVariable UUID restaurantId, @RequestPart("file") MultipartFile file,
                                                               Authentication authentication) {
        return ResponseEntity.ok(coverService.uploadRestaurantCover(authentication, restaurantId, file));
    }

    @DeleteMapping("/cover")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: remove the restaurant card's cover picture")
    public ResponseEntity<CoverResponse> removeRestaurantCover(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(coverService.removeRestaurantCover(authentication, restaurantId));
    }

    @PutMapping(value = "/branches/{branchId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: the branch card's cover picture (PNG, JPEG or WebP, up to 5 MB)")
    public ResponseEntity<CoverResponse> uploadBranchCover(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                           @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.ok(coverService.uploadBranchCover(authentication, restaurantId, branchId, file));
    }

    @DeleteMapping("/branches/{branchId}/cover")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: remove the branch card's cover picture")
    public ResponseEntity<CoverResponse> removeBranchCover(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                           Authentication authentication) {
        return ResponseEntity.ok(coverService.removeBranchCover(authentication, restaurantId, branchId));
    }
}
