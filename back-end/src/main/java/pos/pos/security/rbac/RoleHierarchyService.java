package pos.pos.security.rbac;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleHierarchyService {

    private final RoleRepository roleRepository;

    public long highestActiveRank(UUID userId) {
        return roleRepository.findHighestActiveRankByUserId(userId);
    }

    public boolean isSuperAdmin(UUID userId) {
        return roleRepository.userHasActiveRoleCode(userId, AppRole.SUPER_ADMIN.name());
    }

    public List<Role> getAssignableRoles(UUID userId) {
        if (isSuperAdmin(userId)) {
            return roleRepository.findByIsActiveTrueOrderByRankDescNameAsc();
        }

        return roleRepository.findAssignableRolesForActorRank(highestActiveRank(userId));
    }

    public void assertCanAssignRole(UUID actorUserId, Role targetRole) {
        if (isSuperAdmin(actorUserId)) {
            return;
        }

        long actorRank = highestActiveRank(actorUserId);
        if (actorRank <= targetRole.getRank() || !targetRole.isAssignable() || targetRole.isProtectedRole()) {
            throw new RoleAssignmentNotAllowedException();
        }
    }

    public void assertCanManageUser(UUID actorUserId, UUID targetUserId) {
        if (isSuperAdmin(actorUserId)) {
            return;
        }

        long actorRank = highestActiveRank(actorUserId);
        long targetRank = highestActiveRank(targetUserId);

        if (actorRank <= targetRank || roleRepository.userHasProtectedActiveRole(targetUserId)) {
            throw new UserManagementNotAllowedException();
        }
    }
}
