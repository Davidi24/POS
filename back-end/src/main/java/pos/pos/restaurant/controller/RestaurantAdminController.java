package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import pos.pos.common.dto.PageResponse;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantCardResponse;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantSummaryResponse;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.service.RestaurantAdminService;
import pos.pos.restaurant.service.RestaurantPlatformService;
import pos.pos.restaurant.service.RestaurantSummaryService;

import java.util.UUID;

// checked
@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantAdminController {

    private final RestaurantAdminService restaurantAdminService;
    private final RestaurantSummaryService restaurantSummaryService;
    private final RestaurantPlatformService restaurantPlatformService;
    private final pos.pos.restaurant.service.RestaurantOwnershipTransferService ownershipTransferService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List restaurants with pagination and optional filters")
    public ResponseEntity<PageResponse<RestaurantResponse>> getRestaurants(
            @RequestParam(required = false) String search,          // partial match on restaurant name, code, or slug
            @RequestParam(required = false) Boolean active,         // filter by active/inactive flag; omit for all
            @RequestParam(required = false) String status,          // filter by RestaurantStatus enum value; omit for all
            @RequestParam(required = false) UUID ownerUserId,       // filter to restaurants owned by a specific user
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,   // max 100 per page
            @RequestParam(defaultValue = "createdAt") String sortBy,    // field to sort by (e.g. createdAt, name)
            @RequestParam(defaultValue = "desc") String direction,       // asc or desc
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.getRestaurants(
                authentication,
                search,
                active,
                status,
                ownerUserId,
                page,
                size,
                sortBy,
                direction
        ));
    }

    @GetMapping("/cards")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: every restaurant with its city, branches, staff and owner")
    public ResponseEntity<PageResponse<RestaurantCardResponse>> getRestaurantCards(
            @RequestParam(required = false) @Size(max = 100, message = "search must be at most 100 characters") String search,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantPlatformService.cards(authentication, search, filter, page, size));
    }

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: the order of the restaurant cards (listed ones first, the others after them)")
    public ResponseEntity<Void> reorderRestaurants(@Valid @RequestBody RestaurantOrderRequest request, Authentication authentication) {
        restaurantPlatformService.reorder(authentication, request.restaurantIds());
        return ResponseEntity.noContent().build();
    }

    public record RestaurantOrderRequest(
            @jakarta.validation.constraints.NotNull(message = "restaurantIds is required")
            @Size(max = 1000, message = "at most 1000 restaurants")
            java.util.List<@jakarta.validation.constraints.NotNull UUID> restaurantIds
    ) {
    }

    @GetMapping("/{restaurantId}/branches/cards")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "The branches with their picture, address, staff and devices (the \"Choose a branch\" page), a page at a time")
    public ResponseEntity<pos.pos.restaurant.dto.BranchCardPageResponse> getBranchCards(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) @Size(max = 100, message = "search must be at most 100 characters") String search,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "24") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantPlatformService.branchCards(authentication, restaurantId, search, page, size));
    }

    @PostMapping("/{restaurantId}/owner")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: add the restaurant's owner (second step of setting it up); they get an email to set a password")
    public ResponseEntity<RestaurantResponse> createOwner(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateRestaurantOwnerRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantPlatformService.createOwner(authentication, restaurantId, request));
    }

    @PutMapping("/{restaurantId}/owner")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: correct the owner's name, phone or (while the invitation waits) email")
    public ResponseEntity<RestaurantResponse> updateOwner(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody pos.pos.restaurant.dto.UpdateRestaurantOwnerRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantPlatformService.updateOwner(authentication, restaurantId, request));
    }

    @PostMapping("/{restaurantId}/owner/transfer")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super Admin: give the restaurant to someone already in the POS or to a new invited person; the former owner loses access")
    public ResponseEntity<pos.pos.restaurant.dto.RestaurantOwnerTransferResponse> transferOwnership(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody pos.pos.restaurant.dto.TransferRestaurantOwnerRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ownershipTransferService.transfer(authentication, restaurantId, request));
    }

    @GetMapping("/{restaurantId}/owner/transfers")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: every change of the restaurant's owner, newest first")
    public ResponseEntity<java.util.List<pos.pos.restaurant.dto.RestaurantOwnerTransferResponse>> ownershipTransfers(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ownershipTransferService.transfers(authentication, restaurantId));
    }

    @GetMapping("/{restaurantId}/owner/candidates")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: active staff of the restaurant who could become its owner (at most 50)")
    public ResponseEntity<java.util.List<pos.pos.restaurant.dto.OwnerCandidateResponse>> ownerCandidates(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) @Size(max = 100, message = "search must be at most 100 characters") String search,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ownershipTransferService.candidates(authentication, restaurantId, search));
    }

    @GetMapping("/{restaurantId}/owner/invite")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: whether the owner's invitation email went out and whether its link still works")
    public ResponseEntity<pos.pos.restaurant.dto.OwnerInviteStatusResponse> ownerInvite(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(restaurantPlatformService.ownerInvite(authentication, restaurantId));
    }

    @PostMapping("/{restaurantId}/owner/invite")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Send the owner a new invitation (only before they first sign in)")
    public ResponseEntity<Void> resendOwnerInvite(@PathVariable UUID restaurantId, Authentication authentication) {
        restaurantPlatformService.resendOwnerInvite(authentication, restaurantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_CREATE')")
    @Operation(summary = "Create a restaurant")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantAdminService.createRestaurant(authentication, request));
    }

    @GetMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Get one restaurant by id")
    public ResponseEntity<RestaurantResponse> getRestaurant(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.getRestaurant(authentication, restaurantId));
    }

    @PutMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a restaurant")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateRestaurantRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.updateRestaurant(authentication, restaurantId, request));
    }

    @PatchMapping("/{restaurantId}/status")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update restaurant status")
    public ResponseEntity<RestaurantResponse> updateRestaurantStatus(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateRestaurantStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.updateRestaurantStatus(authentication, restaurantId, request));
    }

    @DeleteMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_DELETE')")
    @Operation(summary = "Soft delete a restaurant")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable UUID restaurantId, Authentication authentication) {
        restaurantAdminService.deleteRestaurant(authentication, restaurantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-slug/{slug}")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Look up a restaurant by its URL slug")
    public ResponseEntity<RestaurantResponse> getRestaurantBySlug(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.getRestaurantBySlug(authentication, slug));
    }

    @GetMapping("/{restaurantId}/summary")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Get restaurant summary metrics for dashboard use")
    public ResponseEntity<RestaurantSummaryResponse> getRestaurantSummary(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantSummaryService.getSummary(authentication, restaurantId));
    }
}
