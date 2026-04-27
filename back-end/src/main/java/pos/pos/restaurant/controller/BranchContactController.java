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
import pos.pos.restaurant.service.BranchContactService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/contacts")
@RequiredArgsConstructor
public class BranchContactController {

    private final BranchContactService branchContactService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List branch contacts")
    public ResponseEntity<List<ContactResponse>> getBranchContacts(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(branchContactService.getContacts(authentication, restaurantId, branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create a branch contact")
    public ResponseEntity<ContactResponse> createBranchContact(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpsertContactRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchContactService.createContact(authentication, restaurantId, branchId, request));
    }

    @PutMapping("/{contactId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a branch contact")
    public ResponseEntity<ContactResponse> updateBranchContact(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID contactId,
            @Valid @RequestBody UpsertContactRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                branchContactService.updateContact(authentication, restaurantId, branchId, contactId, request)
        );
    }

    @PatchMapping("/{contactId}/primary")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Mark a branch contact as primary")
    public ResponseEntity<ContactResponse> makePrimary(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID contactId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                branchContactService.makePrimary(authentication, restaurantId, branchId, contactId)
        );
    }

    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Delete a branch contact")
    public ResponseEntity<Void> deleteBranchContact(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID contactId,
            Authentication authentication
    ) {
        branchContactService.deleteContact(authentication, restaurantId, branchId, contactId);
        return ResponseEntity.noContent().build();
    }
}
