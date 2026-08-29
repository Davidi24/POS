package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryCountCreateRequest;
import pos.pos.inventory.dto.InventoryCountLineUpsertRequest;
import pos.pos.inventory.dto.InventoryCountResponse;
import pos.pos.inventory.enums.InventoryCountStatus;
import pos.pos.inventory.service.InventoryCountService;

import java.util.List;
import java.util.UUID;

// A physical stock count moves through DRAFT -> IN_PROGRESS -> COMPLETED -> APPROVED (or
// CANCELLED before APPROVED). Each transition is its own endpoint on purpose, instead of a
// generic "update status" endpoint, so each one can enforce its own preconditions and effects
// (e.g. approve is the only one that touches live stock levels).
@Tag(name = "Inventory Counts")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/counts")
@RequiredArgsConstructor
public class InventoryCountController {

    private final InventoryCountService inventoryCountService;

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Start a new physical stock count (created as DRAFT)")
    @ApiResponse(responseCode = "201", description = "Count created")
    public ResponseEntity<InventoryCountResponse> createCount(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryCountCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryCountService.createCount(authentication, restaurantId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List counts, optionally filtered by status")
    public ResponseEntity<List<InventoryCountResponse>> listCounts(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) InventoryCountStatus status,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.listCounts(authentication, restaurantId, status));
    }

    @GetMapping("/{countId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one count, with its lines")
    @ApiResponse(responseCode = "200", description = "Count found")
    @ApiResponse(responseCode = "404", description = "Count not found")
    public ResponseEntity<InventoryCountResponse> getCount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.getCount(authentication, restaurantId, countId));
    }

    @PostMapping("/{countId}/start")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Start counting (DRAFT -> IN_PROGRESS)")
    @ApiResponse(responseCode = "200", description = "Count started")
    @ApiResponse(responseCode = "409", description = "Count is not currently DRAFT")
    public ResponseEntity<InventoryCountResponse> startCount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.startCount(authentication, restaurantId, countId));
    }

    @PutMapping("/{countId}/lines/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Add or update one item's counted quantity on this count")
    @ApiResponse(responseCode = "200", description = "Line saved")
    @ApiResponse(responseCode = "409", description = "Count is not DRAFT or IN_PROGRESS")
    public ResponseEntity<InventoryCountResponse> upsertLine(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryCountLineUpsertRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.upsertLine(authentication, restaurantId, countId, itemId, request));
    }

    @DeleteMapping("/{countId}/lines/{lineId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Remove a line from this count")
    @ApiResponse(responseCode = "200", description = "Line removed")
    @ApiResponse(responseCode = "409", description = "Count is not DRAFT or IN_PROGRESS")
    public ResponseEntity<InventoryCountResponse> removeLine(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            @PathVariable UUID lineId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.removeLine(authentication, restaurantId, countId, lineId));
    }

    @PostMapping("/{countId}/complete")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Finish counting (IN_PROGRESS -> COMPLETED)")
    @ApiResponse(responseCode = "200", description = "Count completed")
    @ApiResponse(responseCode = "409", description = "Count is not currently IN_PROGRESS")
    public ResponseEntity<InventoryCountResponse> completeCount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.completeCount(authentication, restaurantId, countId));
    }

    // NOTE: uses SETTINGS_UPDATE for now, same as every other write in this controller. This
    // project doesn't have a manager-level "approve" permission yet (checked the full permission
    // set -- nothing like INVENTORY_APPROVE exists), so there's currently no way to require a
    // stricter permission just for this one action. Worth revisiting once one exists.
    @PostMapping("/{countId}/approve")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Approve the count and correct live stock levels (COMPLETED -> APPROVED)")
    @ApiResponse(responseCode = "200", description = "Count approved, stock corrected")
    @ApiResponse(responseCode = "409", description = "Count is not currently COMPLETED")
    public ResponseEntity<InventoryCountResponse> approveCount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.approveCount(authentication, restaurantId, countId));
    }

    @PostMapping("/{countId}/cancel")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Cancel the count")
    @ApiResponse(responseCode = "200", description = "Count cancelled")
    @ApiResponse(responseCode = "409", description = "An approved count cannot be cancelled")
    public ResponseEntity<InventoryCountResponse> cancelCount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID countId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryCountService.cancelCount(authentication, restaurantId, countId));
    }
}
