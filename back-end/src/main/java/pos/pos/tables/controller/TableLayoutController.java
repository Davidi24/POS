package pos.pos.tables.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.tables.dto.AutoArrangeTableLayoutRequest;
import pos.pos.tables.dto.FloorSummaryResponse;
import pos.pos.tables.dto.TableLayoutResponse;
import pos.pos.tables.dto.TableMapResponse;
import pos.pos.tables.dto.UpdateFloorsRequest;
import pos.pos.tables.dto.UpdateTableLayoutRequest;
import pos.pos.tables.service.RestaurantTableService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tables")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}")
@RequiredArgsConstructor
public class TableLayoutController {

    private final RestaurantTableService restaurantTableService;

    @GetMapping("/table-layout")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get branch table layout")
    public ResponseEntity<TableLayoutResponse> getTableLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTableLayout(authentication, restaurantId, branchId));
    }

    @PutMapping("/table-layout")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace parts of branch table layout")
    public ResponseEntity<TableLayoutResponse> updateTableLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateTableLayoutRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTableLayout(authentication, restaurantId, branchId, request));
    }

    @PostMapping("/table-layout/auto-arrange")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Auto arrange table positions")
    public ResponseEntity<TableLayoutResponse> autoArrangeTableLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody(required = false) AutoArrangeTableLayoutRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.autoArrangeTableLayout(
                authentication,
                restaurantId,
                branchId,
                request == null ? AutoArrangeTableLayoutRequest.builder().build() : request
        ));
    }

    @GetMapping("/floors")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch floors derived from table layout")
    public ResponseEntity<List<FloorSummaryResponse>> getFloors(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getFloors(authentication, restaurantId, branchId));
    }

    @PutMapping("/floors")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Rename branch floors derived from table layout")
    public ResponseEntity<List<FloorSummaryResponse>> updateFloors(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateFloorsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.updateFloors(authentication, restaurantId, branchId, request));
    }

    @GetMapping("/tables/map")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get grouped table map by floor")
    public ResponseEntity<TableMapResponse> getTableMap(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTableMap(authentication, restaurantId, branchId));
    }
}
