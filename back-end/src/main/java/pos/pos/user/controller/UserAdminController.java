package pos.pos.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.common.dto.PageResponse;
import pos.pos.role.dto.RoleResponse;
import pos.pos.user.dto.ReplaceUserRolesRequest;
import pos.pos.user.dto.UpdateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.service.UserAdminService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users")
@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "List users with pagination and optional filters")
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String roleCode,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userAdminService.getUsers(
                authentication,
                search,
                active,
                roleCode,
                page,
                size,
                sortBy,
                direction
        ));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "Get one user by id")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId, Authentication authentication) {
        return ResponseEntity.ok(userAdminService.getUser(authentication, userId));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Update mutable user fields")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userAdminService.updateUser(authentication, userId, request));
    }

    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "List the user's active roles")
    public ResponseEntity<List<RoleResponse>> getUserRoles(@PathVariable UUID userId, Authentication authentication) {
        return ResponseEntity.ok(userAdminService.getUserRoles(authentication, userId));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Replace the user's active roles")
    public ResponseEntity<UserResponse> replaceUserRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplaceUserRolesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userAdminService.replaceUserRoles(authentication, userId, request));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('USERS_DELETE')")
    @Operation(summary = "Soft delete a user")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId, Authentication authentication) {
        userAdminService.deleteUser(authentication, userId);
        return ResponseEntity.noContent().build();
    }
}
