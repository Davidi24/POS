package pos.pos.recipe.controller;

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
import pos.pos.recipe.dto.RecipeResponse;
import pos.pos.recipe.service.RecipePhotoService;

import java.util.UUID;

@Tag(name = "Recipes")
@RestController
@RequestMapping("/restaurants/{restaurantId}/recipes/{recipeId}/image")
@RequiredArgsConstructor
public class RecipePhotoController {

    private final RecipePhotoService photoService;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "The house-made item's photo (PNG, JPEG or WebP, up to 5 MB); replaces the old one")
    public ResponseEntity<RecipeResponse> upload(@PathVariable UUID restaurantId, @PathVariable UUID recipeId,
                                                 @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.ok(photoService.upload(authentication, restaurantId, recipeId, file));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Remove the house-made item's photo")
    public ResponseEntity<RecipeResponse> remove(@PathVariable UUID restaurantId, @PathVariable UUID recipeId,
                                                  Authentication authentication) {
        return ResponseEntity.ok(photoService.remove(authentication, restaurantId, recipeId));
    }
}
