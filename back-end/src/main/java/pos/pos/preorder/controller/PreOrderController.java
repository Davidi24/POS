package pos.pos.preorder.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.preorder.dto.PreOrderActionRequest;
import pos.pos.preorder.dto.PreOrderRequest;
import pos.pos.preorder.dto.PreOrderResponse;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.service.PreOrderService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Pre-orders")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class PreOrderController {

    private final PreOrderService preOrderService;

    @GetMapping("/reservations/{reservationId}/pre-order")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get a reservation's pre-order (the live one, otherwise the latest)")
    public ResponseEntity<PreOrderResponse> getPreOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(preOrderService.getForReservation(authentication, restaurantId, reservationId));
    }

    @PutMapping("/reservations/{reservationId}/pre-order")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "Place or replace a reservation's pre-order while it hasn't gone to the kitchen")
    public ResponseEntity<PreOrderResponse> placePreOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody PreOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(preOrderService.placeForReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/reservations/{reservationId}/pre-order/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    @Operation(summary = "Cancel a reservation's pre-order and refund it (only before it goes to the kitchen)")
    public ResponseEntity<PreOrderResponse> cancelPreOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) PreOrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(preOrderService.cancelForReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/reservations/{reservationId}/pre-order/send")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Send a reservation's pre-order to the kitchen now instead of at its lead time")
    public ResponseEntity<PreOrderResponse> sendPreOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(preOrderService.sendNow(authentication, restaurantId, reservationId));
    }

    @GetMapping("/branches/{branchId}/pre-orders")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List pre-orders for bookings in a time window, earliest booking first")
    public ResponseEntity<List<PreOrderResponse>> getBranchPreOrders(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) PreOrderStatus status,
            Authentication authentication
    ) {
        return ResponseEntity.ok(preOrderService.getForBranch(authentication, restaurantId, branchId, from, to, status));
    }
}
