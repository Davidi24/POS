package pos.pos.security.rbac;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;


//checked
//tested
@Service
@RequiredArgsConstructor
public class RoleHierarchyService {

    private final RoleRepository roleRepository;


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

        return roleRepository.findAssignableRolesForActorRank(highestActiveRank(currentUserId(authentication)));
    }

    // it takes the rank of the user role, and it checks the role that he wants to access is it below it rank
    // or is it assignable or is protected if one of this throw error.
    public void assertCanAssignRole(Authentication authentication, Role targetRole) {
        if (isSuperAdmin(authentication)) {
            return;
        }

        long actorRank = highestActiveRank(currentUserId(authentication));
        if (actorRank <= targetRole.getRank() || !targetRole.isAssignable() || targetRole.isProtectedRole()) {
            throw new RoleAssignmentNotAllowedException();
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

        if (actorRank <= targetRank || roleRepository.userHasProtectedActiveRole(targetUserId)) {
            throw new UserManagementNotAllowedException();
        }
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
