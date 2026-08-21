package pos.pos.tables.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.tables.dto.FloorLayoutRequest;
import pos.pos.tables.dto.FloorLayoutResponse;
import pos.pos.tables.realtime.TableLayoutChangeNotifier;
import pos.pos.tables.service.FloorLayoutService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Floor Layouts")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/floor-layouts")
@RequiredArgsConstructor
public class FloorLayoutController {

    private final FloorLayoutService floorLayoutService;
    private final TableLayoutChangeNotifier layoutChangeNotifier;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS_READ', 'ORDER_READ')")
    @Operation(summary = "List floor layouts for a branch")
    public ResponseEntity<List<FloorLayoutResponse>> getFloorLayouts(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                floorLayoutService.getFloorLayouts(authentication, restaurantId, branchId)
        );
    }

    @GetMapping("/{floorLayoutId}")
    @PreAuthorize("hasAnyAuthority('SETTINGS_READ', 'ORDER_READ')")
    @Operation(summary = "Get one floor layout")
    public ResponseEntity<FloorLayoutResponse> getFloorLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID floorLayoutId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                floorLayoutService.getFloorLayout(authentication, restaurantId, branchId, floorLayoutId)
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Create a floor layout")
    public ResponseEntity<FloorLayoutResponse> createFloorLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody FloorLayoutRequest request,
            Authentication authentication
    ) {
        FloorLayoutResponse response = floorLayoutService.createFloorLayout(
                authentication,
                restaurantId,
                branchId,
                request
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{floorLayoutId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Update a floor layout")
    public ResponseEntity<FloorLayoutResponse> updateFloorLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID floorLayoutId,
            @Valid @RequestBody FloorLayoutRequest request,
            Authentication authentication
    ) {
        FloorLayoutResponse response = floorLayoutService.updateFloorLayout(
                authentication,
                restaurantId,
                branchId,
                floorLayoutId,
                request
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
    }

    @PutMapping(
            path = "/{floorLayoutId}/plan-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Upload or replace a floor plan image")
    public ResponseEntity<FloorLayoutResponse> uploadPlanImage(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID floorLayoutId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        FloorLayoutResponse response = floorLayoutService.uploadPlanImage(
                authentication,
                restaurantId,
                branchId,
                floorLayoutId,
                file
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{floorLayoutId}/plan-image")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Remove a floor plan image")
    public ResponseEntity<FloorLayoutResponse> removePlanImage(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID floorLayoutId,
            Authentication authentication
    ) {
        FloorLayoutResponse response = floorLayoutService.removePlanImage(
                authentication,
                restaurantId,
                branchId,
                floorLayoutId
        );
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{floorLayoutId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE') or hasRole('MANAGER')")
    @Operation(summary = "Delete a floor layout")
    public ResponseEntity<Void> deleteFloorLayout(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID floorLayoutId,
            Authentication authentication
    ) {
        floorLayoutService.deleteFloorLayout(authentication, restaurantId, branchId, floorLayoutId);
        layoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.noContent().build();
    }
}
