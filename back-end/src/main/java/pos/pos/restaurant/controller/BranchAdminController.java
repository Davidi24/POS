package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.CreateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchStatusRequest;
import pos.pos.restaurant.service.BranchAdminService;

import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches")
@RequiredArgsConstructor
public class BranchAdminController {

    private final BranchAdminService branchAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List branches for a restaurant")
    public ResponseEntity<PageResponse<BranchResponse>> getBranches(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID managerUserId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication
    ) {
        return ResponseEntity.ok(branchAdminService.getBranches(
                authentication,
                restaurantId,
                search,
                active,
                status,
                managerUserId,
                page,
                size,
                sortBy,
                direction
        ));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create a branch")
    public ResponseEntity<BranchResponse> createBranch(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateBranchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchAdminService.createBranch(authentication, restaurantId, request));
    }

    @GetMapping("/{branchId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Get one branch")
    public ResponseEntity<BranchResponse> getBranch(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(branchAdminService.getBranch(authentication, restaurantId, branchId));
    }

    @PutMapping("/{branchId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a branch")
    public ResponseEntity<BranchResponse> updateBranch(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(branchAdminService.updateBranch(authentication, restaurantId, branchId, request));
    }

    @PatchMapping("/{branchId}/status")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update branch status")
    public ResponseEntity<BranchResponse> updateBranchStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                branchAdminService.updateBranchStatus(authentication, restaurantId, branchId, request)
        );
    }

    @DeleteMapping("/{branchId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Soft delete a branch")
    public ResponseEntity<Void> deleteBranch(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        branchAdminService.deleteBranch(authentication, restaurantId, branchId);
        return ResponseEntity.noContent().build();
    }
}
