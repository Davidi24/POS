package pos.pos.tables.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.tables.dto.AutoArrangeTableLayoutRequest;
import pos.pos.tables.dto.FloorSummaryResponse;
import pos.pos.tables.dto.TableLayoutResponse;
import pos.pos.tables.dto.TableMapResponse;
import pos.pos.tables.dto.UpdateFloorsRequest;
import pos.pos.tables.dto.UpdateTableLayoutRequest;
import pos.pos.tables.realtime.TableLayoutChangeNotifier;
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
    private final TableLayoutChangeNotifier layoutChangeNotifier;

    @GetMapping(
            path = "/table-layout/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @PreAuthorize("hasAnyAuthority('SETTINGS_READ', 'ORDER_READ')")
    @Operation(summary = "Subscribe to branch table-layout changes")
    public SseEmitter subscribeToTableLayoutChanges(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return layoutChangeNotifier.subscribe(
                authentication,
                restaurantId,
                branchId
        );
    }

    @GetMapping("/table-layout")
    @PreAuthorize("hasAnyAuthority('SETTINGS_READ', 'ORDER_READ')")
    @Operation(summary = "Get branch table layout")
    public ResponseEntity<TableLayoutResponse> getTableLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTableService.getTableLayout(authentication, restaurantId, branchId));
    }

    @PutMapping("/table-layout")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Replace parts of branch table layout")
    public ResponseEntity<TableLayoutResponse> updateTableLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateTableLayoutRequest request,
            Authentication authentication
    ) {
        TableLayoutResponse response = restaurantTableService.updateTableLayout(
                authentication,
                restaurantId,
                branchId,
                request
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/table-layout/auto-arrange")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Auto arrange table positions")
    public ResponseEntity<TableLayoutResponse> autoArrangeTableLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody(required = false) AutoArrangeTableLayoutRequest request,
            Authentication authentication
    ) {
        TableLayoutResponse response = restaurantTableService.autoArrangeTableLayout(
                authentication,
                restaurantId,
                branchId,
                request == null ? AutoArrangeTableLayoutRequest.builder().build() : request
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
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
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Rename branch floors derived from table layout")
    public ResponseEntity<List<FloorSummaryResponse>> updateFloors(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateFloorsRequest request,
            Authentication authentication
    ) {
        List<FloorSummaryResponse> response = restaurantTableService.updateFloors(
                authentication,
                restaurantId,
                branchId,
                request
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
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
