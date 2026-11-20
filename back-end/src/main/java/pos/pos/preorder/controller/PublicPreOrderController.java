package pos.pos.preorder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.preorder.dto.PreOrderActionRequest;
import pos.pos.preorder.dto.PreOrderRequest;
import pos.pos.preorder.dto.PreOrderResponse;
import pos.pos.preorder.service.PreOrderService;

// For guests, addressed by the reservation code they received when booking.
@Tag(name = "Public Pre-orders")
@Validated
@RestController
@RequestMapping("/public/reservations/{reservationCode}/pre-order")
@RequiredArgsConstructor
public class PublicPreOrderController {

    private final PreOrderService preOrderService;

    @GetMapping
    @Operation(summary = "Get the pre-order for a reservation code")
    public ResponseEntity<PreOrderResponse> getPreOrder(@PathVariable String reservationCode) {
        return ResponseEntity.ok(preOrderService.getPublic(reservationCode));
    }

    @PutMapping
    @Operation(summary = "Place or replace the pre-order for a reservation code (paid in full for now; online payment comes later)")
    public ResponseEntity<PreOrderResponse> placePreOrder(
            @PathVariable String reservationCode,
            @Valid @RequestBody PreOrderRequest request
    ) {
        return ResponseEntity.ok(preOrderService.placePublic(reservationCode, request));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel the pre-order for a reservation code and refund it (only before it goes to the kitchen)")
    public ResponseEntity<PreOrderResponse> cancelPreOrder(
            @PathVariable String reservationCode,
            @Valid @RequestBody(required = false) PreOrderActionRequest request
    ) {
        return ResponseEntity.ok(preOrderService.cancelPublic(reservationCode, request));
    }
}
