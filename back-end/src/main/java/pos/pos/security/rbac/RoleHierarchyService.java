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
    private final pos.pos.user.repository.UserRepository userRepository;


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
        // A Co-Super-Admin hands out every restaurant role (Owner included), never the platform roles.
        if (isCoSuperAdmin(authentication)) {
            return roleRepository.findByIsActiveTrueOrderByRankDescNameAsc().stream()
                    .filter(role -> role.getRank() < AppRole.CO_SUPER_ADMIN.rank())
                    .toList();
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        return roleRepository.findAssignableRolesForActorRank(actorRank).stream()
                .filter(role -> visibleTo(authentication, role))
                .filter(role -> meetsRoleFloor(authentication, actorRank, role))
                .toList();
    }

    // it takes the rank of the user role, and it checks the role that he wants to access is it below it rank
    // or is it assignable or is protected if one of this throw error.
    public void assertCanAssignRole(Authentication authentication, Role targetRole) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (isCoSuperAdmin(authentication)) {
            if (targetRole.getRank() >= AppRole.CO_SUPER_ADMIN.rank()) {
                throw new RoleAssignmentNotAllowedException();
            }
            return;
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        // Roles below your own, and your own level too (a manager may add another manager, then manages them).
        if (!visibleTo(authentication, targetRole)
                || actorRank < targetRole.getRank()
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
        // A Co-Super-Admin manages any restaurant's own roles, never the built-in or platform ones.
        if (isCoSuperAdmin(authentication)) {
            if (targetRole.isSystem() || targetRole.getRank() >= AppRole.CO_SUPER_ADMIN.rank()) {
                throw new RoleManagementNotAllowedException();
            }
            return;
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        UUID actorRestaurantId = actorRestaurantId(authentication);
        // Only the restaurant that made a custom role changes it.
        if (actorRestaurantId == null || !actorRestaurantId.equals(targetRole.getRestaurantId())
                || actorRank <= targetRole.getRank() || targetRole.isProtectedRole() || targetRole.isSystem()) {
            throw new RoleManagementNotAllowedException();
        }
    }

    /**
     * Changing a built-in role's permissions inside one restaurant (for that restaurant only): super admins working in it,
     * or someone there ranked above the role. The protected and platform roles never change.
     */
    public void assertCanChangeRoleInRestaurant(Authentication authentication, Role targetRole) {
        if (targetRole.isProtectedRole() || targetRole.getRank() >= AppRole.CO_SUPER_ADMIN.rank()) {
            throw new RoleManagementNotAllowedException();
        }
        if (isPlatformAdmin(authentication)) {
            return;
        }
        if (highestActiveRank(currentUserId(authentication)) <= targetRole.getRank()) {
            throw new RoleManagementNotAllowedException();
        }
    }

    // it checks if the current user can manage the user that it's targeting. if the user that is targeting
    // has at least one protected role return false.
    public void assertCanManageUser(Authentication authentication, UUID targetUserId) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        // A Co-Super-Admin reaches everyone in every restaurant (owners too), but never the Super Admin or another
        // Co-Super-Admin.
        if (isCoSuperAdmin(authentication)) {
            if (highestActiveRank(targetUserId) >= AppRole.CO_SUPER_ADMIN.rank()) {
                throw new UserManagementNotAllowedException();
            }
            return;
        }

        // Staff of one restaurant never reach the people of another, whatever their rank.
        if (!sameRestaurant(authentication, targetUserId)) {
            throw new UserManagementNotAllowedException();
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        long targetRank = highestActiveRank(targetUserId);

        // People below you, and people at your own level whom you added yourself; never anyone above you, nor the
        // person who added you.
        boolean peerYouAdded = targetRank == actorRank && createdBy(targetUserId, currentUserId(authentication));
        if ((actorRank <= targetRank && !peerYouAdded)
                || roleRepository.userHasProtectedActiveRole(targetUserId)
                || roleRepository.findActiveRolesByUserId(targetUserId).stream()
                        .anyMatch(role -> !meetsRoleFloor(authentication, actorRank, role))) {
            throw new UserManagementNotAllowedException();
        }
    }

    // A role is visible to everyone when it's shared (system roles), otherwise only to its own restaurant.
    // Super admins see every restaurant's roles, or inside a restaurant they entered only that restaurant's.
    public boolean visibleTo(Authentication authentication, Role role) {
        if (role.getRestaurantId() == null) {
            return true;
        }
        if (isPlatformAdmin(authentication) && actorRestaurantId(authentication) == null) {
            return true;
        }
        return role.getRestaurantId().equals(actorRestaurantId(authentication));
    }

    // True when the target person works at the actor's restaurant (an actor without a restaurant reaches nobody).
    // For the Super Admin and Co-Super-Admins this is the restaurant they entered in the app.
    public boolean sameRestaurant(Authentication authentication, UUID targetUserId) {
        UUID actorRestaurantId = actorRestaurantId(authentication);
        if (actorRestaurantId == null || targetUserId == null) {
            return false;
        }
        return userRepository.findById(targetUserId)
                .map(target -> actorRestaurantId.equals(target.getRestaurantId()))
                .orElse(false);
    }

    public UUID actorRestaurantId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.getRestaurantId();
    }

    // Some system roles can only be handed out or managed from a set level up (Managers from Co-Owner, Viewers from Admin),
    // not by anyone who merely outranks them. Admins reach Managers only when their restaurant switches it on.
    private boolean meetsRoleFloor(Authentication authentication, long actorRank, Role role) {
        // Everyone may hand out their own level (and manage the people they add with it).
        if (role.getRank() == actorRank) {
            return true;
        }
        AppRole appRole = role.isSystem() ? AppRole.fromCode(role.getCode()) : null;
        if (appRole == null || appRole.lowestManagingRole() == null || actorRank >= appRole.lowestManagingRole().rank()) {
            return true;
        }

        return appRole == AppRole.MANAGER
                && actorRank >= AppRole.ADMIN.rank()
                && adminsCanManageManagers(authentication);
    }

    private boolean createdBy(UUID targetUserId, UUID actorId) {
        return userRepository.findById(targetUserId).map(target -> actorId.equals(target.getCreatedBy())).orElse(false);
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

    public boolean isCoSuperAdmin(Authentication authentication) {
        String coSuperAdminAuthority = "ROLE_" + AppRole.CO_SUPER_ADMIN.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(coSuperAdminAuthority::equals);
    }

    // Super Admin or Co-Super-Admin: they reach every restaurant (their permissions still apply).
    public boolean isPlatformAdmin(Authentication authentication) {
        return isSuperAdmin(authentication) || isCoSuperAdmin(authentication);
    }

    // take the authorities that are assigned in jwt filter and check if one of them is Super_Admin
    public boolean isSuperAdmin(Authentication authentication) {
        String superAdminAuthority = "ROLE_" + AppRole.SUPER_ADMIN.name();

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(superAdminAuthority::equals);
    }
}
