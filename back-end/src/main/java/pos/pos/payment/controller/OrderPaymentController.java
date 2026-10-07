package pos.pos.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.payment.dto.OrderPaymentSummaryResponse;
import pos.pos.payment.dto.PaymentResponse;
import pos.pos.payment.dto.ReceiptResponse;
import pos.pos.payment.dto.RefundPaymentRequest;
import pos.pos.payment.dto.TakePaymentRequest;
import pos.pos.payment.dto.TakePaymentResponse;
import pos.pos.payment.dto.VoidPaymentRequest;
import pos.pos.payment.service.PaymentQueryService;
import pos.pos.payment.service.PaymentService;

import java.util.UUID;

/**
 * Paying an order. These paths sit under /orders so the order write filter serialises them per restaurant and
 * replays a retried request with the same Idempotency-Key instead of charging twice.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}")
@Tag(name = "Payments", description = "Take, refund and cancel payments on orders; receipts")
public class OrderPaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService paymentQueryService;

    @GetMapping("/orders/{orderId}/payments")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "What is paid and what is left on an order, with every payment")
    public ResponseEntity<OrderPaymentSummaryResponse> getOrderPayments(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentService.getOrderPayments(authentication, restaurantId, orderId));
    }

    @PostMapping("/orders/{orderId}/payments")
    @PreAuthorize("hasAuthority('ORDER_CLOSE')")
    @Operation(summary = "Take a payment (cash, card, ...) towards the bill; send an Idempotency-Key to retry safely")
    public ResponseEntity<TakePaymentResponse> takePayment(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody TakePaymentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.takePayment(authentication, restaurantId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/payments/{paymentId}/refund")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    @Operation(summary = "Give money back on a payment, with a reason and Idempotency-Key")
    public ResponseEntity<OrderPaymentSummaryResponse> refundPayment(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundPaymentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentService.refundPayment(authentication, restaurantId, orderId, paymentId, request));
    }

    @PostMapping("/orders/{orderId}/payments/{paymentId}/void")
    @PreAuthorize("hasAuthority('ORDER_VOID')")
    @Operation(summary = "Cancel a payment taken by mistake (same day, nothing refunded yet), with a reason and Idempotency-Key")
    public ResponseEntity<OrderPaymentSummaryResponse> voidPayment(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody VoidPaymentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentService.voidPayment(authentication, restaurantId, orderId, paymentId, request));
    }

    @GetMapping("/orders/{orderId}/receipt")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "The receipt for an order, filtered by the restaurant's receipt settings")
    public ResponseEntity<ReceiptResponse> getReceipt(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentQueryService.receipt(authentication, restaurantId, orderId));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "One payment with its history (sale, refunds, cancel)")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID restaurantId,
            @PathVariable UUID paymentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentService.getPayment(authentication, restaurantId, paymentId));
    }
}
