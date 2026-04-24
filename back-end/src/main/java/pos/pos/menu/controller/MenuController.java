package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
import pos.pos.common.dto.PageResponse;
import pos.pos.menu.dto.CreateMenuRequest;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.dto.UpdateMenuRequest;
import pos.pos.menu.dto.UpdateMenuStatusRequest;
import pos.pos.menu.service.MenuService;

import java.util.UUID;

@Tag(name = "Menus")
@Validated
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "List menus with pagination and optional filters")
    public ResponseEntity<PageResponse<MenuResponse>> getMenus(
            Authentication authentication,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return ResponseEntity.ok(menuService.getMenus(authentication, restaurantId, active, search, page, size, sortBy, direction));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENUS_CREATE')")
    @Operation(summary = "Create a menu")
    public ResponseEntity<MenuResponse> createMenu(
            @Valid @RequestBody CreateMenuRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuService.createMenu(authentication, request));
    }

    @GetMapping("/{menuId}")
    @PreAuthorize("hasAuthority('MENUS_READ')")
    @Operation(summary = "Get one menu by id")
    public ResponseEntity<MenuResponse> getMenu(
            Authentication authentication,
            @PathVariable UUID menuId,
            @RequestParam(defaultValue = "false") boolean includeSections,
            @RequestParam(defaultValue = "false") boolean includeItems
    ) {
        return ResponseEntity.ok(menuService.getMenu(authentication, menuId, includeSections, includeItems));
    }

    @PutMapping("/{menuId}")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update a menu")
    public ResponseEntity<MenuResponse> updateMenu(
            @PathVariable UUID menuId,
            @Valid @RequestBody UpdateMenuRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(menuService.updateMenu(authentication, menuId, request));
    }

    @PatchMapping("/{menuId}/status")
    @PreAuthorize("hasAuthority('MENUS_UPDATE')")
    @Operation(summary = "Update only the active status of a menu")
    public ResponseEntity<MenuResponse> updateMenuStatus(
            @PathVariable UUID menuId,
            @Valid @RequestBody UpdateMenuStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(menuService.updateMenuStatus(authentication, menuId, request));
    }

    @DeleteMapping("/{menuId}")
    @PreAuthorize("hasAuthority('MENUS_DELETE')")
    @Operation(summary = "Delete a menu")
    public ResponseEntity<Void> deleteMenu(
            @PathVariable UUID menuId,
            Authentication authentication
    ) {
        menuService.deleteMenu(authentication, menuId);
        return ResponseEntity.noContent().build();
    }
}
