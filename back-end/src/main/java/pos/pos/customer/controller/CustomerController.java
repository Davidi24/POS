package pos.pos.customer.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.customer.dto.CustomerRequest;
import pos.pos.customer.dto.CustomerResponse;
import pos.pos.customer.service.CustomerService;
import pos.pos.reservation.dto.ReservationResponse;

import java.util.List;
import java.util.UUID;

@Tag(name = "Customers")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List restaurant customers")
    public ResponseEntity<List<CustomerResponse>> getCustomers(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(customerService.getCustomers(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a restaurant customer")
    public ResponseEntity<CustomerResponse> createCustomer(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CustomerRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerService.createCustomer(authentication, restaurantId, request));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one restaurant customer")
    public ResponseEntity<CustomerResponse> getCustomer(
            @PathVariable UUID restaurantId,
            @PathVariable UUID customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(customerService.getCustomer(authentication, restaurantId, customerId));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a restaurant customer")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable UUID restaurantId,
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(customerService.updateCustomer(authentication, restaurantId, customerId, request));
    }

    @GetMapping("/{customerId}/reservations")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List reservations for one customer")
    public ResponseEntity<List<ReservationResponse>> getCustomerReservations(
            @PathVariable UUID restaurantId,
            @PathVariable UUID customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(customerService.getCustomerReservations(authentication, restaurantId, customerId));
    }

    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Soft-delete a restaurant customer")
    public ResponseEntity<Void> deleteCustomer(
            @PathVariable UUID restaurantId,
            @PathVariable UUID customerId,
            Authentication authentication
    ) {
        customerService.deleteCustomer(authentication, restaurantId, customerId);
        return ResponseEntity.noContent().build();
    }
}
