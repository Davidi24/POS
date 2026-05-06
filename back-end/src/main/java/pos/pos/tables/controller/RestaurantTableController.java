package pos.pos.tables.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.dto.TableMergeRequest;
import pos.pos.tables.dto.TableRequest;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.dto.UpdateTablePositionRequest;
import pos.pos.tables.dto.UpdateTableQrCodeRequest;
import pos.pos.tables.dto.UpdateTableStatusRequest;
import pos.pos.tables.service.RestaurantTableService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Tables")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService restaurantTableService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch tables")
    public ResponseEntity<List<TableResponse>> getTables(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTables(authentication, restaurantId, branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a branch table")
    public ResponseEntity<TableResponse> createTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody TableRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantTableService.createTable(authentication, restaurantId, branchId, request));
    }

    @GetMapping("/{tableId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one branch table")
    public ResponseEntity<TableResponse> getTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTable(authentication, restaurantId, branchId, tableId));
    }

    @PutMapping("/{tableId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a branch table")
    public ResponseEntity<TableResponse> updateTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody TableRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTable(authentication, restaurantId, branchId, tableId, request));
    }

    @PatchMapping("/{tableId}/status")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update a branch table status")
    public ResponseEntity<TableResponse> updateTableStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTableStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTableStatus(authentication, restaurantId, branchId, tableId, request));
    }

    @PatchMapping("/{tableId}/position")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update a branch table position")
    public ResponseEntity<TableResponse> updateTablePosition(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTablePositionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTablePosition(authentication, restaurantId, branchId, tableId, request));
    }

    @PatchMapping("/{tableId}/qr-code")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update a branch table QR code value")
    public ResponseEntity<TableResponse> updateTableQrCode(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTableQrCodeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTableQrCode(authentication, restaurantId, branchId, tableId, request));
    }

    @DeleteMapping("/{tableId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete a branch table")
    public ResponseEntity<Void> deleteTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        restaurantTableService.deleteTable(authentication, restaurantId, branchId, tableId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List table availability for a requested window")
    public ResponseEntity<List<TableAvailabilityResponse>> getTableAvailability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer partySize,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTableAvailability(authentication, restaurantId, branchId, from, to, partySize));
    }

    @GetMapping("/available")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List available tables for a requested window")
    public ResponseEntity<List<TableAvailabilityResponse>> getAvailableTables(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer partySize,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getAvailableTables(authentication, restaurantId, branchId, from, to, partySize));
    }

    @GetMapping("/{tableId}/availability")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one table availability for a requested window")
    public ResponseEntity<TableAvailabilityResponse> getTableAvailability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer partySize,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTableAvailability(authentication, restaurantId, branchId, tableId, from, to, partySize));
    }

    @PostMapping("/{tableId}/block")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Block a branch table")
    public ResponseEntity<TableResponse> blockTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.blockTable(authentication, restaurantId, branchId, tableId));
    }

    @PostMapping("/{tableId}/unblock")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Unblock a branch table")
    public ResponseEntity<TableResponse> unblockTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.unblockTable(authentication, restaurantId, branchId, tableId));
    }

    @PostMapping("/{tableId}/mark-clean")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Mark a branch table as clean")
    public ResponseEntity<TableResponse> markTableClean(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.markTableClean(authentication, restaurantId, branchId, tableId));
    }

    @PostMapping("/{tableId}/mark-dirty")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Mark a branch table as dirty")
    public ResponseEntity<TableResponse> markTableDirty(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.markTableDirty(authentication, restaurantId, branchId, tableId));
    }

    @PostMapping("/{tableId}/merge")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Merge child tables into a primary table")
    public ResponseEntity<TableResponse> mergeTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody TableMergeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.mergeTable(authentication, restaurantId, branchId, tableId, request));
    }

    @PostMapping("/{tableId}/unmerge")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Unmerge a child table or all child tables from a primary table")
    public ResponseEntity<TableResponse> unmergeTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.unmergeTable(authentication, restaurantId, branchId, tableId));
    }
}
