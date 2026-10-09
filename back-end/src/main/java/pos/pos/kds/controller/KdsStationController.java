package pos.pos.kds.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.kds.dto.KdsAssignableDeviceResponse;
import pos.pos.kds.dto.KdsStationResponse;
import pos.pos.kds.dto.UpsertKdsStationRequest;
import pos.pos.kds.service.KdsStationCommandService;
import pos.pos.kds.service.KdsStationQueryService;

import java.util.List;
import java.util.UUID;

@Tag(name = "KDS Stations")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/kds")
@RequiredArgsConstructor
public class KdsStationController {

    private final KdsStationQueryService kdsStationQueryService;
    private final KdsStationCommandService kdsStationCommandService;

    @GetMapping("/stations")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List KDS stations for one branch")
    public ResponseEntity<List<KdsStationResponse>> getStations(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Authentication authentication
    ) {
        return ResponseEntity.ok(kdsStationQueryService.getStations(authentication, restaurantId, branchId, activeOnly));
    }

    @GetMapping("/stations/{stationId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one KDS station")
    public ResponseEntity<KdsStationResponse> getStation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID stationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(kdsStationQueryService.getStation(authentication, restaurantId, branchId, stationId));
    }

    @PostMapping("/stations")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create one KDS station")
    public ResponseEntity<KdsStationResponse> createStation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpsertKdsStationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kdsStationCommandService.createStation(authentication, restaurantId, branchId, request));
    }

    @PutMapping("/stations/{stationId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace one KDS station")
    public ResponseEntity<KdsStationResponse> updateStation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID stationId,
            @Valid @RequestBody UpsertKdsStationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsStationCommandService.updateStation(authentication, restaurantId, branchId, stationId, request)
        );
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List assignable KDS devices for one branch")
    public ResponseEntity<List<KdsAssignableDeviceResponse>> getAssignableDevices(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(kdsStationQueryService.getAssignableDevices(authentication, restaurantId, branchId));
    }
}
