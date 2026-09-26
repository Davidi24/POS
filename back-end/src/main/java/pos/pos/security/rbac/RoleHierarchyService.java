package pos.pos.security.rbac;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.exception.role.RoleManagementNotAllowedException;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;

import java.util.List;
import java.util.UUID;


//checked
//tested
@Service
@RequiredArgsConstructor
public class RoleHierarchyService {

    private final RoleRepository roleRepository;
    private final SettingsRepository settingsRepository;


    // takes the highest rank of all roles that a user has
    public long highestActiveRank(UUID userId) {
        return roleRepository.findHighestActiveRankByUserId(userId);
    }

    public long actorRank(Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return Long.MAX_VALUE;
        }

        return highestActiveRank(currentUserId(authentication));
    }

    // it returns all roles that a user can assign. (for super admin it returns all roles tha are active)
    public List<Role> getAssignableRoles(Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return roleRepository.findByIsActiveTrueOrderByRankDescNameAsc();
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        return roleRepository.findAssignableRolesForActorRank(actorRank).stream()
                .filter(role -> meetsRoleFloor(authentication, actorRank, role))
                .toList();
    }

    // it takes the rank of the user role, and it checks the role that he wants to access is it below it rank
    // or is it assignable or is protected if one of this throw error.
    public void assertCanAssignRole(Authentication authentication, Role targetRole) {
        if (isSuperAdmin(authentication)) {
            return;
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        if (actorRank <= targetRole.getRank()
                || !targetRole.isAssignable()
                || targetRole.isProtectedRole()
                || !meetsRoleFloor(authentication, actorRank, targetRole)) {
            throw new RoleAssignmentNotAllowedException();
        }
    }

    public void assertCanManageRole(Authentication authentication, Role targetRole) {
        if (isSuperAdmin(authentication)) {
            return;
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        if (actorRank <= targetRole.getRank() || targetRole.isProtectedRole() || targetRole.isSystem()) {
            throw new RoleManagementNotAllowedException();
        }
    }

    // it checks if the current user can manage the user that it's targeting. if the user that is targeting
    // has at least one protected role return false.
    public void assertCanManageUser(Authentication authentication, UUID targetUserId) {
        if (isSuperAdmin(authentication)) {
            return;
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        long targetRank = highestActiveRank(targetUserId);

        if (actorRank <= targetRank
                || roleRepository.userHasProtectedActiveRole(targetUserId)
                || roleRepository.findActiveRolesByUserId(targetUserId).stream()
                        .anyMatch(role -> !meetsRoleFloor(authentication, actorRank, role))) {
            throw new UserManagementNotAllowedException();
        }
    }

    // Some system roles can only be handed out or managed from a set level up (Managers from Co-Owner, Viewers from Admin),
    // not by anyone who merely outranks them. Admins reach Managers only when their restaurant switches it on.
    private boolean meetsRoleFloor(Authentication authentication, long actorRank, Role role) {
        AppRole appRole = role.isSystem() ? AppRole.fromCode(role.getCode()) : null;
        if (appRole == null || appRole.lowestManagingRole() == null || actorRank >= appRole.lowestManagingRole().rank()) {
            return true;
        }

        return appRole == AppRole.MANAGER
                && actorRank >= AppRole.ADMIN.rank()
                && adminsCanManageManagers(authentication);
    }

    // The Owner/Co-Owner switch in the actor's restaurant settings; off when there is no restaurant or no settings yet.
    private boolean adminsCanManageManagers(Authentication authentication) {
        UUID restaurantId = ((AuthenticatedUser) authentication.getPrincipal()).getRestaurantId();
        return restaurantId != null && settingsRepository.findByRestaurant_Id(restaurantId)
                .map(Settings::isAdminsCanManageManagers)
                .orElse(false);
    }

    // return user id from Authentication object
    public UUID currentUserId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).getId();
    }

    // take the authorities that are assigned in jwt filter and check if one of them is Super_Admin
    public boolean isSuperAdmin(Authentication authentication) {
        String superAdminAuthority = "ROLE_" + AppRole.SUPER_ADMIN.name();

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(superAdminAuthority::equals);
    }
}
