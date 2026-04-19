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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.common.dto.PageResponse;
import pos.pos.role.dto.RoleResponse;
import pos.pos.user.dto.AdminPasswordResetRequest;
import pos.pos.user.dto.ClientTargetRequest;
import pos.pos.user.dto.ReplaceUserRolesRequest;
import pos.pos.user.dto.UpdateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.service.UserAdminActionService;
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
    private final UserAdminActionService userAdminActionService;

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

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Trigger a password reset for a user")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) AdminPasswordResetRequest request,
            Authentication authentication
    ) {
        userAdminActionService.requestPasswordReset(authentication, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/send-verification-email")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Send an email verification message for a user")
    public ResponseEntity<Void> sendVerificationEmail(
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) ClientTargetRequest request,
            Authentication authentication
    ) {
        userAdminActionService.sendVerificationEmail(authentication, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/send-phone-verification")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Send a phone verification code for a user")
    public ResponseEntity<Void> sendPhoneVerification(@PathVariable UUID userId, Authentication authentication) {
        userAdminActionService.sendPhoneVerification(authentication, userId);
        return ResponseEntity.noContent().build();
    }
}
