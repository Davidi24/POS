package pos.pos.unit.security.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleHierarchyService")
class RoleHierarchyServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleHierarchyService roleHierarchyService;

    @Nested
    @DisplayName("highestActiveRank")
    class HighestActiveRankTests {

        @Test
        @DisplayName("Should return the highest active rank for the requested user")
        void shouldReturnHighestActiveRank() {
            UUID userId = UUID.randomUUID();

            when(roleRepository.findHighestActiveRankByUserId(userId)).thenReturn(20_000L);

            long result = roleHierarchyService.highestActiveRank(userId);

            assertThat(result).isEqualTo(20_000L);
            verify(roleRepository).findHighestActiveRankByUserId(userId);
        }
    }

    @Nested
    @DisplayName("getAssignableRoles")
    class GetAssignableRolesTests {

        @Test
        @DisplayName("Should return all active roles for super admin without querying actor role code")
        void shouldReturnAllRolesForSuperAdmin() {
            List<Role> roles = List.of(role("ADMIN", 30_000L), role("MANAGER", 20_000L));

            when(roleRepository.findByIsActiveTrueOrderByRankDescNameAsc()).thenReturn(roles);

            List<Role> result = roleHierarchyService.getAssignableRoles(authentication(true));

            assertThat(result).isEqualTo(roles);
            verify(roleRepository).findByIsActiveTrueOrderByRankDescNameAsc();
            verify(roleRepository, never()).findHighestActiveRankByUserId(any());
            verify(roleRepository, never()).userHasActiveRoleCode(any(), anyString());
        }

        @Test
        @DisplayName("Should return lower-ranked assignable roles for non-super-admin")
        void shouldReturnAssignableRolesForNonSuperAdmin() {
            UUID actorUserId = UUID.randomUUID();
            List<Role> roles = List.of(role("CASHIER", 10_000L));

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);
            when(roleRepository.findAssignableRolesForActorRank(20_000L)).thenReturn(roles);

            List<Role> result = roleHierarchyService.getAssignableRoles(authentication(actorUserId, false));

            assertThat(result).isEqualTo(roles);
            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
            verify(roleRepository).findAssignableRolesForActorRank(20_000L);
            verify(roleRepository, never()).userHasActiveRoleCode(any(), anyString());
        }
    }

    @Nested
    @DisplayName("assertCanAssignRole")
    class AssertCanAssignRoleTests {

        @Test
        @DisplayName("Should allow super admin without querying actor role code")
        void shouldAllowSuperAdmin() {
            Role targetRole = role("ADMIN", 30_000L);
            targetRole.setAssignable(false);
            targetRole.setProtectedRole(true);

            assertThatCode(() -> roleHierarchyService.assertCanAssignRole(authentication(true), targetRole))
                    .doesNotThrowAnyException();

            verify(roleRepository, never()).findHighestActiveRankByUserId(any());
            verify(roleRepository, never()).userHasActiveRoleCode(any(), anyString());
        }

        @Test
        @DisplayName("Should allow non-super-admin to assign lower-ranked assignable unprotected role")
        void shouldAllowNonSuperAdminToAssignLowerRankedRole() {
            UUID actorUserId = UUID.randomUUID();
            Role targetRole = role("CASHIER", 10_000L);

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);

            assertThatCode(() -> roleHierarchyService.assertCanAssignRole(
                    authentication(actorUserId, false),
                    targetRole
            )).doesNotThrowAnyException();

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
            verify(roleRepository, never()).userHasActiveRoleCode(any(), anyString());
        }

        @Test
        @DisplayName("Should deny non-super-admin when target role rank is not lower")
        void shouldDenyNonSuperAdminWhenTargetRoleRankIsNotLower() {
            UUID actorUserId = UUID.randomUUID();
            Role targetRole = role("MANAGER", 20_000L);

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);

            assertThatThrownBy(() -> roleHierarchyService.assertCanAssignRole(
                    authentication(actorUserId, false),
                    targetRole
            )).isInstanceOf(RoleAssignmentNotAllowedException.class);

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
        }

        @Test
        @DisplayName("Should deny non-super-admin when target role is not assignable")
        void shouldDenyNonSuperAdminWhenTargetRoleIsNotAssignable() {
            UUID actorUserId = UUID.randomUUID();
            Role targetRole = role("AUDITOR", 10_000L);
            targetRole.setAssignable(false);

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);

            assertThatThrownBy(() -> roleHierarchyService.assertCanAssignRole(
                    authentication(actorUserId, false),
                    targetRole
            )).isInstanceOf(RoleAssignmentNotAllowedException.class);

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
        }

        @Test
        @DisplayName("Should deny non-super-admin when target role is protected")
        void shouldDenyNonSuperAdminWhenTargetRoleIsProtected() {
            UUID actorUserId = UUID.randomUUID();
            Role targetRole = role("OWNER", 10_000L);
            targetRole.setProtectedRole(true);

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);

            assertThatThrownBy(() -> roleHierarchyService.assertCanAssignRole(
                    authentication(actorUserId, false),
                    targetRole
            )).isInstanceOf(RoleAssignmentNotAllowedException.class);

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
        }
    }

    @Nested
    @DisplayName("assertCanManageUser")
    class AssertCanManageUserTests {

        @Test
        @DisplayName("Should allow super admin without querying actor role code")
        void shouldAllowSuperAdmin() {
            UUID targetUserId = UUID.randomUUID();

            assertThatCode(() -> roleHierarchyService.assertCanManageUser(authentication(true), targetUserId))
                    .doesNotThrowAnyException();

            verify(roleRepository, never()).findHighestActiveRankByUserId(any());
            verify(roleRepository, never()).userHasProtectedActiveRole(any());
            verify(roleRepository, never()).userHasActiveRoleCode(any(), anyString());
        }

        @Test
        @DisplayName("Should allow non-super-admin to manage lower-ranked unprotected user")
        void shouldAllowNonSuperAdminToManageLowerRankedUnprotectedUser() {
            UUID actorUserId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(30_000L);
            when(roleRepository.findHighestActiveRankByUserId(targetUserId)).thenReturn(20_000L);
            when(roleRepository.userHasProtectedActiveRole(targetUserId)).thenReturn(false);

            assertThatCode(() -> roleHierarchyService.assertCanManageUser(
                    authentication(actorUserId, false),
                    targetUserId
            )).doesNotThrowAnyException();

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
            verify(roleRepository).findHighestActiveRankByUserId(targetUserId);
            verify(roleRepository).userHasProtectedActiveRole(targetUserId);
        }

        @Test
        @DisplayName("Should deny non-super-admin when target user rank is equal")
        void shouldDenyNonSuperAdminWhenTargetUserRankIsEqual() {
            UUID actorUserId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);
            when(roleRepository.findHighestActiveRankByUserId(targetUserId)).thenReturn(20_000L);

            assertThatThrownBy(() -> roleHierarchyService.assertCanManageUser(
                    authentication(actorUserId, false),
                    targetUserId
            )).isInstanceOf(UserManagementNotAllowedException.class);

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
            verify(roleRepository).findHighestActiveRankByUserId(targetUserId);
            verify(roleRepository, never()).userHasProtectedActiveRole(targetUserId);
        }

        @Test
        @DisplayName("Should deny non-super-admin when target user rank is higher")
        void shouldDenyNonSuperAdminWhenTargetUserRankIsHigher() {
            UUID actorUserId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(20_000L);
            when(roleRepository.findHighestActiveRankByUserId(targetUserId)).thenReturn(30_000L);

            assertThatThrownBy(() -> roleHierarchyService.assertCanManageUser(
                    authentication(actorUserId, false),
                    targetUserId
            )).isInstanceOf(UserManagementNotAllowedException.class);

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
            verify(roleRepository).findHighestActiveRankByUserId(targetUserId);
            verify(roleRepository, never()).userHasProtectedActiveRole(targetUserId);
        }

        @Test
        @DisplayName("Should deny non-super-admin when target user has protected role")
        void shouldDenyNonSuperAdminWhenTargetUserHasProtectedRole() {
            UUID actorUserId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(30_000L);
            when(roleRepository.findHighestActiveRankByUserId(targetUserId)).thenReturn(20_000L);
            when(roleRepository.userHasProtectedActiveRole(targetUserId)).thenReturn(true);

            assertThatThrownBy(() -> roleHierarchyService.assertCanManageUser(
                    authentication(actorUserId, false),
                    targetUserId
            )).isInstanceOf(UserManagementNotAllowedException.class);

            verify(roleRepository).findHighestActiveRankByUserId(actorUserId);
            verify(roleRepository).findHighestActiveRankByUserId(targetUserId);
            verify(roleRepository).userHasProtectedActiveRole(targetUserId);
        }
    }

    private Authentication authentication(boolean superAdmin) {
        return authentication(UUID.randomUUID(), superAdmin);
    }

    private Authentication authentication(UUID userId, boolean superAdmin) {
        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(userId)
                .email("user@pos.local")
                .active(true)
                .build();

        List<SimpleGrantedAuthority> authorities = superAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_MANAGER"));

        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }

    private Role role(String code, long rank) {
        return Role.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(code)
                .rank(rank)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build();
    }
}
