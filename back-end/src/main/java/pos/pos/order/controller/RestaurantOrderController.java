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
import pos.pos.order.dto.CreateOrderDiscountRequest;
import pos.pos.order.dto.CreateOrderItemOptionRequest;
import pos.pos.order.dto.CreateOrderLineItemRequest;
import pos.pos.order.dto.CreateOrderRequest;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.dto.OrderAuditResponse;
import pos.pos.order.dto.OrderCustomerRequest;
import pos.pos.order.dto.OrderDiscountResponse;
import pos.pos.order.dto.OrderEventResponse;
import pos.pos.order.dto.OrderItemOptionResponse;
import pos.pos.order.dto.OrderLineItemNotesRequest;
import pos.pos.order.dto.OrderLineItemQuantityRequest;
import pos.pos.order.dto.OrderLineItemResponse;
import pos.pos.order.dto.OrderMergeRequest;
import pos.pos.order.dto.OrderNextNumberResponse;
import pos.pos.order.dto.OrderPaymentStatusRequest;
import pos.pos.order.dto.OrderReservationRequest;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderSplitPreviewResponse;
import pos.pos.order.dto.OrderSplitRequest;
import pos.pos.order.dto.OrderSummaryResponse;
import pos.pos.order.dto.OrderTableRequest;
import pos.pos.order.dto.OrderTotalsResponse;
import pos.pos.order.dto.OrderTransferBranchRequest;
import pos.pos.order.dto.OrderTransferTableRequest;
import pos.pos.order.dto.OrderValidationResponse;
import pos.pos.order.dto.UpdateOrderLineItemStatusRequest;
import pos.pos.order.dto.UpdateOrderRequest;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderCommandService;
import pos.pos.order.service.OrderItemService;
import pos.pos.order.service.OrderQueryService;
import pos.pos.order.service.OrderWorkflowService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Orders")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class RestaurantOrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;
    private final OrderWorkflowService orderWorkflowService;
    private final OrderItemService orderItemService;

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List restaurant orders")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getOrders(authentication, restaurantId, from, to));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get one order by id")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getOrder(authentication, restaurantId, orderId));
    }

    @GetMapping("/orders/number/{orderNumber}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get one order by order number")
    public ResponseEntity<OrderResponse> getOrderByNumber(
            @PathVariable UUID restaurantId,
            @PathVariable String orderNumber,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getOrderByNumber(authentication, restaurantId, orderNumber));
    }

    @GetMapping("/customers/{customerId}/orders")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List orders for one customer")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(
            @PathVariable UUID restaurantId,
            @PathVariable UUID customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getCustomerOrders(authentication, restaurantId, customerId));
    }

    @PatchMapping("/orders/{orderId}")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Patch an order header")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderCommandService.updateOrder(authentication, restaurantId, orderId, request));
    }

    @PatchMapping("/orders/{orderId}/customer")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Update the order customer link")
    public ResponseEntity<OrderResponse> updateOrderCustomer(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody OrderCustomerRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderCommandService.updateOrderCustomer(authentication, restaurantId, orderId, request));
    }

    @PatchMapping("/orders/{orderId}/table")
    @PreAuthorize("hasAuthority('ORDER_TRANSFER')")
    @Operation(summary = "Update the order table link")
    public ResponseEntity<OrderResponse> updateOrderTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody OrderTableRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderCommandService.updateOrderTable(authentication, restaurantId, orderId, request));
    }

    @PatchMapping("/orders/{orderId}/reservation")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Update the order reservation link")
    public ResponseEntity<OrderResponse> updateOrderReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody OrderReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderCommandService.updateOrderReservation(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/validate")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "Validate a proposed order")
    public ResponseEntity<OrderValidationResponse> validateOrder(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderCommandService.validateOrder(authentication, restaurantId, request));
    }

    @PostMapping("/orders/next-number")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "Generate the next order number")
    public ResponseEntity<OrderNextNumberResponse> nextOrderNumber(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.nextOrderNumber(authentication, restaurantId));
    }

    @PostMapping("/orders/{orderId}/open")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Open a draft order")
    public ResponseEntity<OrderResponse> openOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.openOrder(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/send-to-kitchen")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Send an order to the kitchen")
    public ResponseEntity<OrderResponse> sendToKitchen(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.sendToKitchen(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/ready")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Mark an order as ready")
    public ResponseEntity<OrderResponse> markOrderReady(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.markOrderReady(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/fulfill")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Mark an order as fulfilled")
    public ResponseEntity<OrderResponse> fulfillOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.fulfillOrder(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/close")
    @PreAuthorize("hasAuthority('ORDER_CLOSE')")
    @Operation(summary = "Close an order")
    public ResponseEntity<OrderResponse> closeOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.closeOrder(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/reopen")
    @PreAuthorize("hasAuthority('ORDER_REOPEN')")
    @Operation(summary = "Reopen a closed order")
    public ResponseEntity<OrderResponse> reopenOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.reopenOrder(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.cancelOrder(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/void")
    @PreAuthorize("hasAuthority('ORDER_VOID')")
    @Operation(summary = "Void an order")
    public ResponseEntity<OrderResponse> voidOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.voidOrder(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/merge")
    @PreAuthorize("hasAuthority('ORDER_TRANSFER')")
    @Operation(summary = "Merge another order into this one")
    public ResponseEntity<OrderResponse> mergeOrders(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderMergeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.mergeOrders(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/transfer/table")
    @PreAuthorize("hasAuthority('ORDER_TRANSFER')")
    @Operation(summary = "Transfer an order to another table")
    public ResponseEntity<OrderResponse> transferOrderTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderTransferTableRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.transferOrderTable(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/transfer/branch")
    @PreAuthorize("hasAuthority('ORDER_TRANSFER')")
    @Operation(summary = "Transfer an order to another branch")
    public ResponseEntity<OrderResponse> transferOrderBranch(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderTransferBranchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.transferOrderBranch(authentication, restaurantId, orderId, request));
    }

    @GetMapping("/orders/{orderId}/items")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List order line items")
    public ResponseEntity<List<OrderLineItemResponse>> getItems(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getItems(authentication, restaurantId, orderId));
    }

    @PostMapping("/orders/{orderId}/items")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Add a line item to an order")
    public ResponseEntity<OrderLineItemResponse> addItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateOrderLineItemRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderItemService.addItem(authentication, restaurantId, orderId, request));
    }

    @GetMapping("/orders/{orderId}/items/{lineItemId}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get one order line item")
    public ResponseEntity<OrderLineItemResponse> getItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getItem(authentication, restaurantId, orderId, lineItemId));
    }

    @PutMapping("/orders/{orderId}/items/{lineItemId}")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Replace one order line item")
    public ResponseEntity<OrderLineItemResponse> updateItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @Valid @RequestBody CreateOrderLineItemRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.updateItem(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PatchMapping("/orders/{orderId}/items/{lineItemId}/quantity")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Update line item quantity")
    public ResponseEntity<OrderLineItemResponse> updateItemQuantity(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @Valid @RequestBody OrderLineItemQuantityRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.updateItemQuantity(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PatchMapping("/orders/{orderId}/items/{lineItemId}/notes")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Update line item notes")
    public ResponseEntity<OrderLineItemResponse> updateItemNotes(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @RequestBody OrderLineItemNotesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.updateItemNotes(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PatchMapping("/orders/{orderId}/items/{lineItemId}/status")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Update line item status")
    public ResponseEntity<OrderLineItemResponse> updateItemStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @Valid @RequestBody UpdateOrderLineItemStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.updateItemStatus(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PostMapping("/orders/{orderId}/items/{lineItemId}/fire")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Fire one line item")
    public ResponseEntity<OrderLineItemResponse> fireItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.fireItem(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PostMapping("/orders/{orderId}/items/{lineItemId}/ready")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Mark one line item ready")
    public ResponseEntity<OrderLineItemResponse> readyItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.readyItem(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PostMapping("/orders/{orderId}/items/{lineItemId}/fulfill")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Fulfill one line item")
    public ResponseEntity<OrderLineItemResponse> fulfillItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.fulfillItem(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PostMapping("/orders/{orderId}/items/{lineItemId}/void")
    @PreAuthorize("hasAuthority('ORDER_VOID')")
    @Operation(summary = "Void one line item")
    public ResponseEntity<OrderLineItemResponse> voidItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.voidItem(authentication, restaurantId, orderId, lineItemId, request));
    }

    @GetMapping("/orders/{orderId}/items/{lineItemId}/options")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List order item options")
    public ResponseEntity<List<OrderItemOptionResponse>> getItemOptions(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getItemOptions(authentication, restaurantId, orderId, lineItemId));
    }

    @PostMapping("/orders/{orderId}/items/{lineItemId}/options")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Add one order item option")
    public ResponseEntity<OrderItemOptionResponse> addItemOption(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @Valid @RequestBody CreateOrderItemOptionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderItemService.addItemOption(authentication, restaurantId, orderId, lineItemId, request));
    }

    @PutMapping("/orders/{orderId}/items/{lineItemId}/options/{optionId}")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Replace one order item option")
    public ResponseEntity<OrderItemOptionResponse> updateItemOption(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @PathVariable UUID optionId,
            @Valid @RequestBody CreateOrderItemOptionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.updateItemOption(authentication, restaurantId, orderId, lineItemId, optionId, request));
    }

    @DeleteMapping("/orders/{orderId}/items/{lineItemId}/options/{optionId}")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Delete one order item option")
    public ResponseEntity<Void> deleteItemOption(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID lineItemId,
            @PathVariable UUID optionId,
            Authentication authentication
    ) {
        orderItemService.deleteItemOption(authentication, restaurantId, orderId, lineItemId, optionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{orderId}/discounts")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List order discounts")
    public ResponseEntity<List<OrderDiscountResponse>> getDiscounts(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getDiscounts(authentication, restaurantId, orderId));
    }

    @PostMapping("/orders/{orderId}/discounts")
    @PreAuthorize("hasAuthority('ORDER_DISCOUNT_APPLY')")
    @Operation(summary = "Add one order discount")
    public ResponseEntity<OrderDiscountResponse> addDiscount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateOrderDiscountRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderItemService.addDiscount(authentication, restaurantId, orderId, request));
    }

    @PutMapping("/orders/{orderId}/discounts/{discountId}")
    @PreAuthorize("hasAuthority('ORDER_DISCOUNT_APPLY')")
    @Operation(summary = "Replace one order discount")
    public ResponseEntity<OrderDiscountResponse> updateDiscount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID discountId,
            @Valid @RequestBody CreateOrderDiscountRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderItemService.updateDiscount(authentication, restaurantId, orderId, discountId, request));
    }

    @DeleteMapping("/orders/{orderId}/discounts/{discountId}")
    @PreAuthorize("hasAuthority('ORDER_DISCOUNT_APPLY')")
    @Operation(summary = "Delete one order discount")
    public ResponseEntity<Void> deleteDiscount(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID discountId,
            Authentication authentication
    ) {
        orderItemService.deleteDiscount(authentication, restaurantId, orderId, discountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{orderId}/events")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "List order events")
    public ResponseEntity<List<OrderEventResponse>> getEvents(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getEvents(authentication, restaurantId, orderId));
    }

    @PostMapping("/orders/{orderId}/events/notes")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Add one order note event")
    public ResponseEntity<OrderEventResponse> addNoteEvent(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderItemService.addNoteEvent(authentication, restaurantId, orderId, request));
    }

    @GetMapping("/orders/{orderId}/timeline")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get the order timeline")
    public ResponseEntity<List<OrderEventResponse>> getTimeline(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getTimeline(authentication, restaurantId, orderId));
    }

    @GetMapping("/orders/{orderId}/audit")
    @PreAuthorize("hasAuthority('ORDER_AUDIT')")
    @Operation(summary = "Get the order audit response")
    public ResponseEntity<OrderAuditResponse> getAudit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getAudit(authentication, restaurantId, orderId));
    }

    @GetMapping("/orders/{orderId}/totals")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get calculated order totals")
    public ResponseEntity<OrderTotalsResponse> getTotals(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getTotals(authentication, restaurantId, orderId));
    }

    @PostMapping("/orders/{orderId}/payment-status")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Update order payment status")
    public ResponseEntity<OrderResponse> updatePaymentStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderPaymentStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.updatePaymentStatus(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/mark-paid")
    @PreAuthorize("hasAuthority('ORDER_CLOSE')")
    @Operation(summary = "Mark order paid")
    public ResponseEntity<OrderResponse> markPaid(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.markPaid(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/mark-partially-paid")
    @PreAuthorize("hasAuthority('ORDER_CLOSE')")
    @Operation(summary = "Mark order partially paid")
    public ResponseEntity<OrderResponse> markPartiallyPaid(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.markPartiallyPaid(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/mark-refunded")
    @PreAuthorize("hasAuthority('ORDER_CLOSE')")
    @Operation(summary = "Mark order refunded")
    public ResponseEntity<OrderResponse> markRefunded(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.markRefunded(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/split-preview")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Preview a bill split")
    public ResponseEntity<OrderSplitPreviewResponse> splitPreview(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderSplitRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.splitPreview(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/split")
    @PreAuthorize("hasAuthority('ORDER_TRANSFER')")
    @Operation(summary = "Split an order into a new order")
    public ResponseEntity<OrderResponse> splitOrder(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderSplitRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderWorkflowService.splitOrder(authentication, restaurantId, orderId, request));
    }

    @GetMapping("/orders/summary")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get order summary metrics")
    public ResponseEntity<OrderSummaryResponse> getSummary(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication
    ) {
        return ResponseEntity.ok(orderQueryService.getSummary(authentication, restaurantId, branchId, from, to));
    }
}
