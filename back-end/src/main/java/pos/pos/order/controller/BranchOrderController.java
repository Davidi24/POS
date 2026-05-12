package pos.pos.order.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.order.dto.CreateOrderRequest;
import pos.pos.order.dto.OrderExportResponse;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Branch Orders")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}")
@RequiredArgsConstructor
public class BranchOrderController {

    private final OrderService orderService;

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List branch orders")
    public ResponseEntity<List<OrderResponse>> getBranchOrders(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderService.getBranchOrders(authentication, restaurantId, branchId, from, to, status, customerId));
    }

    @GetMapping("/orders/open")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List open branch orders")
    public ResponseEntity<List<OrderResponse>> getBranchOpenOrders(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderService.getBranchOpenOrders(authentication, restaurantId, branchId));
    }

    @GetMapping("/orders/history")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List branch order history")
    public ResponseEntity<List<OrderResponse>> getBranchHistory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderService.getBranchHistory(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/tables/{tableId}/orders/current")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get the current order for one table")
    public ResponseEntity<OrderResponse> getCurrentTableOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderService.getCurrentTableOrder(authentication, restaurantId, branchId, tableId));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "Create one branch order")
    public ResponseEntity<OrderResponse> createBranchOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createBranchOrder(authentication, restaurantId, branchId, request));
    }

    @PostMapping("/tables/{tableId}/orders")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "Create one table order")
    public ResponseEntity<OrderResponse> createTableOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createTableOrder(authentication, restaurantId, branchId, tableId, request));
    }

    @GetMapping("/orders/kitchen-board")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get kitchen-board orders for one branch")
    public ResponseEntity<List<OrderResponse>> getKitchenBoard(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderService.getKitchenBoard(authentication, restaurantId, branchId));
    }

    @GetMapping("/orders/export")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Export branch orders")
    public ResponseEntity<OrderExportResponse> exportOrders(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderService.exportOrders(authentication, restaurantId, branchId, from, to));
    }
}
