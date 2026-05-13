package pos.pos.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.menu.dto.PublicMenuResponse;
import pos.pos.menu.service.PublicMenuService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Public Menus")
@RestController
@RequestMapping("/public/restaurants/{restaurantId}/menus")
@RequiredArgsConstructor
public class PublicMenuController {

    private final PublicMenuService publicMenuService;

    @GetMapping
    @Operation(summary = "List active menus for a restaurant")
    public ResponseEntity<List<PublicMenuResponse>> getMenus(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(publicMenuService.getMenus(restaurantId));
    }

    @GetMapping("/{menuId}")
    @Operation(summary = "Get one active menu for a restaurant")
    public ResponseEntity<PublicMenuResponse> getMenu(
            @PathVariable UUID restaurantId,
            @PathVariable UUID menuId,
            @RequestParam(defaultValue = "true") boolean includeSections,
            @RequestParam(defaultValue = "true") boolean includeItems
    ) {
        return ResponseEntity.ok(publicMenuService.getMenu(restaurantId, menuId, includeSections, includeItems));
    }
}
