package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.response.OnlineMenuResponse;
import pos.pos.menu.dto.response.OnlineMenuSectionResponse;
import pos.pos.menu.dto.update.RenameOnlineMenuSectionRequest;
import pos.pos.menu.dto.update.ReorderOnlineMenuItemsRequest;
import pos.pos.menu.dto.update.ReorderOnlineMenuSectionsRequest;
import pos.pos.menu.service.OnlineMenuService;

import java.util.List;
import java.util.UUID;

// Staff view of the online menu. Dishes are placed from their own form ("Show in online menu"); here staff preview
// the result and organize the online sections.
@Tag(name = "Online Menu")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/online-menu")
@RequiredArgsConstructor
public class OnlineMenuController {

    private final OnlineMenuService onlineMenuService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "Preview the online menu as customers see it today, including dishes hidden right now")
    public ResponseEntity<OnlineMenuResponse> getPreview(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(onlineMenuService.getPreview(authentication, restaurantId));
    }

    @GetMapping("/sections")
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List the online menu's sections")
    public ResponseEntity<List<OnlineMenuSectionResponse>> getSections(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(onlineMenuService.getSections(authentication, restaurantId));
    }

    @PatchMapping("/sections/{sectionId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Rename an online section")
    public ResponseEntity<OnlineMenuSectionResponse> renameSection(
            @PathVariable UUID restaurantId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody RenameOnlineMenuSectionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(onlineMenuService.renameSection(authentication, restaurantId, sectionId, request.getName()));
    }

    @PutMapping("/sections/order")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Reorder the online sections")
    public ResponseEntity<List<OnlineMenuSectionResponse>> reorderSections(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ReorderOnlineMenuSectionsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(onlineMenuService.reorderSections(authentication, restaurantId, request.getSectionIds()));
    }

    @PutMapping("/sections/{sectionId}/items/order")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Reorder the dishes inside one online section")
    public ResponseEntity<OnlineMenuResponse> reorderSectionItems(
            @PathVariable UUID restaurantId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderOnlineMenuItemsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(onlineMenuService.reorderSectionItems(authentication, restaurantId, sectionId, request.getItemIds()));
    }

    @DeleteMapping("/sections/{sectionId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete an empty online section")
    public ResponseEntity<Void> deleteSection(
            @PathVariable UUID restaurantId,
            @PathVariable UUID sectionId,
            Authentication authentication
    ) {
        onlineMenuService.deleteSection(authentication, restaurantId, sectionId);
        return ResponseEntity.noContent().build();
    }
}
