package pos.pos.reservation.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.SeatWaitlistEntryRequest;
import pos.pos.reservation.dto.WaitlistEntryRequest;
import pos.pos.reservation.dto.WaitlistEntryResponse;
import pos.pos.reservation.service.WaitlistService;
import pos.pos.tables.realtime.TableLayoutChangeNotifier;

import java.util.List;
import java.util.UUID;

@Tag(name = "Waitlist")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final TableLayoutChangeNotifier tableLayoutChangeNotifier;

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Walk-ins waiting for a table, longest waiting first")
    public ResponseEntity<List<WaitlistEntryResponse>> getWaiting(@PathVariable UUID restaurantId, @PathVariable UUID branchId, Authentication authentication) {
        return ResponseEntity.ok(waitlistService.getWaiting(authentication, restaurantId, branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Add a walk-in group to the waitlist")
    public ResponseEntity<WaitlistEntryResponse> add(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody WaitlistEntryRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(waitlistService.add(authentication, restaurantId, branchId, request));
    }

    @PostMapping("/{entryId}/seat")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Seat a waiting group at a free table")
    public ResponseEntity<WaitlistEntryResponse> seat(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID entryId,
            @Valid @RequestBody SeatWaitlistEntryRequest request,
            Authentication authentication
    ) {
        WaitlistEntryResponse response = waitlistService.seat(authentication, restaurantId, branchId, entryId, request.getTableId());
        tableLayoutChangeNotifier.notifyBranchChanged(restaurantId, branchId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{entryId}/remove")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "The group left before a table was free")
    public ResponseEntity<Void> remove(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID entryId,
            Authentication authentication
    ) {
        waitlistService.remove(authentication, restaurantId, branchId, entryId);
        return ResponseEntity.noContent().build();
    }
}
