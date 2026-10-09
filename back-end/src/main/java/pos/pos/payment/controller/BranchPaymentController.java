package pos.pos.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.payment.dto.PaymentPageResponse;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.service.PaymentQueryService;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/payments")
@Tag(name = "Payments", description = "Take, refund and cancel payments on orders; receipts")
public class BranchPaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Payments taken at a branch, newest first (your own unless you have ORDER_AUDIT)")
    public ResponseEntity<PaymentPageResponse> getPayments(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentQueryService.branchPayments(
                authentication, restaurantId, branchId, from, to, method, status, staffId, search, page, size));
    }
}
