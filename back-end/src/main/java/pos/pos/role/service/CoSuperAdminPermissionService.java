package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.role.PermissionNotFoundException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.role.dto.CoSuperAdminPermissionResponse;
import pos.pos.role.dto.ReplaceCoSuperAdminPermissionsRequest;
import pos.pos.role.entity.UserPermissionDenial;
import pos.pos.role.repository.RoleRepository;
import pos.pos.role.repository.UserPermissionDenialRepository;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Super Admin's per-person ticks for each Co-Super-Admin. The role grants everything; an unticked permission is
 * stored as a denial for that one person and taken away on every request (see JwtAuthenticationFilter).
 */
@Service
@RequiredArgsConstructor
public class CoSuperAdminPermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPermissionDenialRepository denialRepository;
    private final RoleHierarchyService roleHierarchyService;

    @Transactional(readOnly = true)
    public List<CoSuperAdminPermissionResponse> getPermissions(Authentication authentication, UUID userId) {
        requireCoSuperAdmin(authentication, userId);
        return describe(userId);
    }

    @Transactional
    public List<CoSuperAdminPermissionResponse> replacePermissions(
            Authentication authentication,
            UUID userId,
            ReplaceCoSuperAdminPermissionsRequest request
    ) {
        requireCoSuperAdmin(authentication, userId);

        Set<String> available = roleCodes();
        Set<String> granted = request.getGrantedCodes();
        if (!available.containsAll(granted)) {
            throw new PermissionNotFoundException();
        }

        // Change only what moved, so a permission switched off twice never collides on (user, permission).
        Map<String, UserPermissionDenial> current = denialRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(UserPermissionDenial::getPermissionCode, Function.identity(), (a, b) -> a));
        List<UserPermissionDenial> nowGranted = current.values().stream()
                .filter(denial -> granted.contains(denial.getPermissionCode()))
                .toList();
        denialRepository.deleteAll(nowGranted);

        UUID actorId = roleHierarchyService.currentUserId(authentication);
        List<UserPermissionDenial> nowDenied = available.stream()
                .filter(code -> !granted.contains(code) && !current.containsKey(code))
                .map(code -> {
                    UserPermissionDenial denial = new UserPermissionDenial();
                    denial.setUserId(userId);
                    denial.setPermissionCode(code);
                    denial.setCreatedBy(actorId);
                    return denial;
                })
                .toList();
        denialRepository.saveAll(nowDenied);
        denialRepository.flush();

        return describe(userId);
    }

    private void requireCoSuperAdmin(Authentication authentication, UUID userId) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            throw new AuthException("Only the Super Admin can change a Co-Super-Admin's permissions", HttpStatus.FORBIDDEN);
        }
        userRepository.findActiveById(userId).orElseThrow(UserNotFoundException::new);
        List<String> roles = roleRepository.findActiveRoleCodesByUserId(userId);
        if (roles.contains(AppRole.SUPER_ADMIN.name()) || !roles.contains(AppRole.CO_SUPER_ADMIN.name())) {
            throw new AuthException("This person is not a Co-Super-Admin", HttpStatus.BAD_REQUEST);
        }
    }

    private List<CoSuperAdminPermissionResponse> describe(UUID userId) {
        Set<String> denied = denialRepository.findAllByUserId(userId).stream()
                .map(UserPermissionDenial::getPermissionCode)
                .collect(Collectors.toSet());
        return AppRole.CO_SUPER_ADMIN.permissions().stream()
                .map(permission -> CoSuperAdminPermissionResponse.builder()
                        .code(permission.name())
                        .name(permission.displayName())
                        .description(permission.description())
                        .granted(!denied.contains(permission.name()))
                        .build())
                .toList();
    }

    private static Set<String> roleCodes() {
        return AppRole.CO_SUPER_ADMIN.permissions().stream()
                .map(AppPermission::name)
                .collect(Collectors.toSet());
    }
}
