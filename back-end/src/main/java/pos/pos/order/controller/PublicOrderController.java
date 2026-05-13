package pos.pos.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.PublicOrderCheckoutRequest;
import pos.pos.order.dto.PublicOrderRequest;
import pos.pos.order.service.OrderPublicService;

@Tag(name = "Public Orders")
@Validated
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderPublicService orderPublicService;

    @PostMapping("/restaurants/{restaurantSlug}/branches/{branchCode}/tables/{tableCode}/orders")
    @Operation(summary = "Create a public QR order")
    public ResponseEntity<OrderResponse> createPublicOrder(
            @PathVariable String restaurantSlug,
            @PathVariable String branchCode,
            @PathVariable String tableCode,
            @Valid @RequestBody PublicOrderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderPublicService.createPublicOrder(restaurantSlug, branchCode, tableCode, request));
    }

    @GetMapping("/orders/{orderNumber}")
    @Operation(summary = "Get a public order by order number")
    public ResponseEntity<OrderResponse> getPublicOrder(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderPublicService.getPublicOrder(orderNumber));
    }

    @PostMapping("/orders/{orderNumber}/items")
    @Operation(summary = "Add items to a public order")
    public ResponseEntity<OrderResponse> addPublicItems(
            @PathVariable String orderNumber,
            @Valid @RequestBody PublicOrderRequest request
    ) {
        return ResponseEntity.ok(orderPublicService.addPublicItems(orderNumber, request));
    }

    @PostMapping("/orders/{orderNumber}/checkout")
    @Operation(summary = "Request checkout for a public order")
    public ResponseEntity<OrderResponse> checkoutPublicOrder(
            @PathVariable String orderNumber,
            @RequestBody(required = false) PublicOrderCheckoutRequest request
    ) {
        return ResponseEntity.ok(orderPublicService.checkoutPublicOrder(orderNumber, request));
    }

    @PostMapping("/orders/{orderNumber}/cancel-request")
    @Operation(summary = "Request cancellation for a public order")
    public ResponseEntity<OrderResponse> cancelPublicOrder(
            @PathVariable String orderNumber,
            @RequestBody(required = false) OrderActionRequest request
    ) {
        return ResponseEntity.ok(orderPublicService.cancelPublicOrder(orderNumber, request));
    }
}
