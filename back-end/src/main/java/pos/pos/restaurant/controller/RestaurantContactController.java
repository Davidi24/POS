package pos.pos.restaurant.controller;

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
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.service.RestaurantContactService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/contacts")
@RequiredArgsConstructor
public class RestaurantContactController {

    private final RestaurantContactService restaurantContactService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List restaurant contacts")
    public ResponseEntity<List<ContactResponse>> getContacts(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantContactService.getContacts(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create a restaurant contact")
    public ResponseEntity<ContactResponse> createContact(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpsertContactRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantContactService.createContact(authentication, restaurantId, request));
    }

    @PutMapping("/{contactId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a restaurant contact")
    public ResponseEntity<ContactResponse> updateContact(
            @PathVariable UUID restaurantId,
            @PathVariable UUID contactId,
            @Valid @RequestBody UpsertContactRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                restaurantContactService.updateContact(authentication, restaurantId, contactId, request)
        );
    }

    @PatchMapping("/{contactId}/primary")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Mark a restaurant contact as primary")
    public ResponseEntity<ContactResponse> makePrimary(
            @PathVariable UUID restaurantId,
            @PathVariable UUID contactId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantContactService.makePrimary(authentication, restaurantId, contactId));
    }

    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Delete a restaurant contact")
    public ResponseEntity<Void> deleteContact(
            @PathVariable UUID restaurantId,
            @PathVariable UUID contactId,
            Authentication authentication
    ) {
        restaurantContactService.deleteContact(authentication, restaurantId, contactId);
        return ResponseEntity.noContent().build();
    }
}
