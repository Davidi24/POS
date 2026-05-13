package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.CreateMenuVariantRequest;
import pos.pos.menu.dto.MenuVariantSummaryResponse;
import pos.pos.menu.dto.UpdateMenuVariantRequest;
import pos.pos.menu.service.MenuVariantService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Menu Variants")
@RestController
@RequestMapping("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants")
@RequiredArgsConstructor
public class MenuVariantController {

    private final MenuVariantService menuVariantService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List variants for one menu item")
    public ResponseEntity<List<MenuVariantSummaryResponse>> getVariants(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId
    ) {
        return ResponseEntity.ok(menuVariantService.getVariants(authentication, menuId, sectionId, itemId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create a variant under a menu item")
    public ResponseEntity<MenuVariantSummaryResponse> createVariant(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateMenuVariantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuVariantService.createVariant(authentication, menuId, sectionId, itemId, request));
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update a variant")
    public ResponseEntity<MenuVariantSummaryResponse> updateVariant(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateMenuVariantRequest request
    ) {
        return ResponseEntity.ok(menuVariantService.updateVariant(authentication, menuId, sectionId, itemId, variantId, request));
    }

    @DeleteMapping("/{variantId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete a variant")
    public ResponseEntity<Void> deleteVariant(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @PathVariable UUID variantId
    ) {
        menuVariantService.deleteVariant(authentication, menuId, sectionId, itemId, variantId);
        return ResponseEntity.noContent().build();
    }
}
