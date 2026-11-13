package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.menu.dto.request.CreateMenuItemRequest;
import pos.pos.menu.dto.response.MenuItemSummaryResponse;
import pos.pos.menu.dto.update.UpdateMenuItemAvailabilityRequest;
import pos.pos.menu.dto.update.UpdateMenuItemRequest;
import pos.pos.menu.service.MenuItemService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Menu Items")
@RestController
@RequestMapping("/menus/{menuId}/sections/{sectionId}/items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;
    private final pos.pos.menu.service.MenuItemImportService menuItemImportService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MENUS_READ', 'KDS_READ')")
    @Operation(summary = "List items for one section")
    public ResponseEntity<List<MenuItemSummaryResponse>> getItems(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "false") boolean includeVariants,
            @RequestParam(defaultValue = "false") boolean includeOptionGroups
    ) {
        return ResponseEntity.ok(menuItemService.getItems(
                authentication,
                menuId,
                sectionId,
                available,
                includeVariants,
                includeOptionGroups
        ));
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("hasAnyAuthority('MENUS_READ', 'KDS_READ')")
    @Operation(summary = "Get one item scoped to its section")
    public ResponseEntity<MenuItemSummaryResponse> getItem(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "false") boolean includeVariants,
            @RequestParam(defaultValue = "false") boolean includeOptionGroups
    ) {
        return ResponseEntity.ok(menuItemService.getItem(
                authentication,
                menuId,
                sectionId,
                itemId,
                includeVariants,
                includeOptionGroups
        ));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create an item under a section")
    public ResponseEntity<MenuItemSummaryResponse> createItem(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody CreateMenuItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.createItem(authentication, menuId, sectionId, request));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Copy items from other menus into this section (each copy has its own price)")
    public ResponseEntity<java.util.List<MenuItemSummaryResponse>> importItems(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody pos.pos.menu.dto.request.ImportMenuItemsRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemImportService.importItems(authentication, menuId, sectionId, request.getItemIds()));
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update an item")
    public ResponseEntity<MenuItemSummaryResponse> updateItem(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateMenuItemRequest request
    ) {
        return ResponseEntity.ok(menuItemService.updateItem(authentication, menuId, sectionId, itemId, request));
    }

    @PatchMapping("/{itemId}/availability")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update only the availability of an item")
    public ResponseEntity<MenuItemSummaryResponse> updateItemAvailability(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateMenuItemAvailabilityRequest request
    ) {
        return ResponseEntity.ok(menuItemService.updateItemAvailability(authentication, menuId, sectionId, itemId, request));
    }

    @PutMapping(value = "/{itemId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('MENUS_UPDATE', 'MENUS_CREATE')")
    @Operation(summary = "Upload the item's photo (PNG, JPEG or WebP, up to 5 MB); replaces any earlier photo")
    public ResponseEntity<MenuItemSummaryResponse> uploadItemImage(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(menuItemService.uploadItemImage(authentication, menuId, sectionId, itemId, file));
    }

    @DeleteMapping("/{itemId}/image")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Remove the item's photo")
    public ResponseEntity<MenuItemSummaryResponse> removeItemImage(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId
    ) {
        return ResponseEntity.ok(menuItemService.removeItemImage(authentication, menuId, sectionId, itemId));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete an item")
    public ResponseEntity<Void> deleteItem(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId
    ) {
        menuItemService.deleteItem(authentication, menuId, sectionId, itemId);
        return ResponseEntity.noContent().build();
    }
}
