package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryActivityResponse;
import pos.pos.inventory.service.InventoryActivityService;

import java.util.UUID;

@Tag(name = "Inventory Movements")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory")
@RequiredArgsConstructor
public class InventoryActivityController {

    private final InventoryActivityService activityService;
    private final pos.pos.inventory.service.InventoryChangeNotifier inventoryChangeNotifier;

    @GetMapping(value = "/events", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Subscribe to restaurant inventory changes")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return inventoryChangeNotifier.subscribe(authentication, restaurantId);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Stock received, used by sales and wasted per day (at cost) for the last days, for the overview")
    public ResponseEntity<InventoryActivityResponse> activity(
            @PathVariable UUID restaurantId,
            @RequestParam(defaultValue = "14") @Min(value = InventoryActivityService.MIN_DAYS, message = "days must be at least 7")
            @Max(value = InventoryActivityService.MAX_DAYS, message = "days must be at most 90") int days,
            Authentication authentication
    ) {
        return ResponseEntity.ok(activityService.activity(authentication, restaurantId, days));
    }
}
