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
import pos.pos.menu.dto.CreateMenuItemOptionGroupRequest;
import pos.pos.menu.dto.MenuItemOptionGroupSummaryResponse;
import pos.pos.menu.dto.UpdateMenuItemOptionGroupRequest;
import pos.pos.menu.service.MenuItemOptionGroupService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Menu Item Option Groups")
@RestController
@RequestMapping("/menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups")
@RequiredArgsConstructor
public class MenuItemOptionGroupController {

    private final MenuItemOptionGroupService menuItemOptionGroupService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List assigned option groups for one menu item")
    public ResponseEntity<List<MenuItemOptionGroupSummaryResponse>> getOptionGroups(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId
    ) {
        return ResponseEntity.ok(menuItemOptionGroupService.getOptionGroups(authentication, menuId, sectionId, itemId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Link an option group to a menu item")
    public ResponseEntity<MenuItemOptionGroupSummaryResponse> createOptionGroupLink(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateMenuItemOptionGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemOptionGroupService.createOptionGroupLink(authentication, menuId, sectionId, itemId, request));
    }

    @PutMapping("/{linkId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update a menu item option group link")
    public ResponseEntity<MenuItemOptionGroupSummaryResponse> updateOptionGroupLink(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdateMenuItemOptionGroupRequest request
    ) {
        return ResponseEntity.ok(menuItemOptionGroupService.updateOptionGroupLink(
                authentication,
                menuId,
                sectionId,
                itemId,
                linkId,
                request
        ));
    }

    @DeleteMapping("/{linkId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Remove an option group link from a menu item")
    public ResponseEntity<Void> deleteOptionGroupLink(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @PathVariable UUID itemId,
            @PathVariable UUID linkId
    ) {
        menuItemOptionGroupService.deleteOptionGroupLink(authentication, menuId, sectionId, itemId, linkId);
        return ResponseEntity.noContent().build();
    }
}
