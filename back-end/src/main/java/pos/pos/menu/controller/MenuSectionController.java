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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.CreateMenuSectionRequest;
import pos.pos.menu.dto.MenuSectionSummaryResponse;
import pos.pos.menu.dto.UpdateMenuSectionRequest;
import pos.pos.menu.dto.UpdateMenuSectionStatusRequest;
import pos.pos.menu.service.MenuSectionService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Menu Sections")
@RestController
@RequestMapping("/menus/{menuId}/sections")
@RequiredArgsConstructor
public class MenuSectionController {

    private final MenuSectionService menuSectionService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List sections for one menu")
    public ResponseEntity<List<MenuSectionSummaryResponse>> getSections(
            Authentication authentication,
            @PathVariable UUID menuId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean includeItems
    ) {
        return ResponseEntity.ok(menuSectionService.getSections(authentication, menuId, active, includeItems));
    }

    @GetMapping("/{sectionId}")
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "Get one section scoped to its menu")
    public ResponseEntity<MenuSectionSummaryResponse> getSection(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @RequestParam(defaultValue = "false") boolean includeItems
    ) {
        return ResponseEntity.ok(menuSectionService.getSection(authentication, menuId, sectionId, includeItems));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create a section under a menu")
    public ResponseEntity<MenuSectionSummaryResponse> createSection(
            Authentication authentication,
            @PathVariable UUID menuId,
            @Valid @RequestBody CreateMenuSectionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuSectionService.createSection(authentication, menuId, request));
    }

    @PutMapping("/{sectionId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update a section")
    public ResponseEntity<MenuSectionSummaryResponse> updateSection(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody UpdateMenuSectionRequest request
    ) {
        return ResponseEntity.ok(menuSectionService.updateSection(authentication, menuId, sectionId, request));
    }

    @PatchMapping("/{sectionId}/status")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update only the active status of a section")
    public ResponseEntity<MenuSectionSummaryResponse> updateSectionStatus(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody UpdateMenuSectionStatusRequest request
    ) {
        return ResponseEntity.ok(menuSectionService.updateSectionStatus(authentication, menuId, sectionId, request));
    }

    @DeleteMapping("/{sectionId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete a section")
    public ResponseEntity<Void> deleteSection(
            Authentication authentication,
            @PathVariable UUID menuId,
            @PathVariable UUID sectionId
    ) {
        menuSectionService.deleteSection(authentication, menuId, sectionId);
        return ResponseEntity.noContent().build();
    }
}
