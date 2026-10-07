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
import pos.pos.security.rbac.AppRole;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;

import java.util.List;
import java.util.Optional;
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

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private pos.pos.user.repository.UserRepository userRepository;

    @InjectMocks
    private RoleHierarchyService roleHierarchyService;

    // Every actor and target in these tests work at this restaurant unless a test says otherwise.
    private static final UUID SHARED_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

    @org.junit.jupiter.api.BeforeEach
    void everyoneWorksAtTheSameRestaurant() {
        org.mockito.Mockito.lenient().when(userRepository.findById(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            pos.pos.user.entity.User target = new pos.pos.user.entity.User();
            target.setId(invocation.getArgument(0));
            target.setRestaurantId(SHARED_RESTAURANT_ID);
            return Optional.of(target);
        });
    }

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
        @DisplayName("Should deny managing someone from another restaurant, whatever the ranks")
        void shouldDenyManagingAnotherRestaurantsUser() {
            UUID targetUserId = UUID.randomUUID();
            pos.pos.user.entity.User target = new pos.pos.user.entity.User();
            target.setId(targetUserId);
            target.setRestaurantId(UUID.randomUUID());
            when(userRepository.findById(targetUserId)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> roleHierarchyService.assertCanManageUser(authentication(false), targetUserId))
                    .isInstanceOf(pos.pos.exception.user.UserManagementNotAllowedException.class);
            verify(roleRepository, never()).findHighestActiveRankByUserId(targetUserId);
        }


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

    @Nested
    @DisplayName("Manager and Viewer floors")
    class RoleFloorTests {

        private final UUID actorUserId = UUID.randomUUID();
        private final UUID restaurantId = SHARED_RESTAURANT_ID;

        @Test
        @DisplayName("Should stop an Admin from creating a Manager while the restaurant switch is off")
        void shouldStopAdminAssigningManagerWhenSwitchOff() {
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.ADMIN.rank());
            adminsCanManageManagers(false);

            assertThatThrownBy(() -> roleHierarchyService.assertCanAssignRole(
                    restaurantActor(),
                    systemRole(AppRole.MANAGER)
            )).isInstanceOf(RoleAssignmentNotAllowedException.class);
        }

        @Test
        @DisplayName("Should let an Admin create a Manager once the restaurant switch is on")
        void shouldLetAdminAssignManagerWhenSwitchOn() {
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.ADMIN.rank());
            adminsCanManageManagers(true);

            assertThatCode(() -> roleHierarchyService.assertCanAssignRole(
                    restaurantActor(),
                    systemRole(AppRole.MANAGER)
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should let a Co-Owner create a Manager without reading the switch")
        void shouldLetCoOwnerAssignManager() {
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.CO_OWNER.rank());

            assertThatCode(() -> roleHierarchyService.assertCanAssignRole(
                    restaurantActor(),
                    systemRole(AppRole.MANAGER)
            )).doesNotThrowAnyException();

            verify(settingsRepository, never()).findByRestaurant_Id(any());
        }

        @Test
        @DisplayName("Should let Owner and Co-Owner create Admins")
        void shouldLetCoOwnerAssignAdmin() {
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.CO_OWNER.rank());

            assertThatCode(() -> roleHierarchyService.assertCanAssignRole(
                    restaurantActor(),
                    systemRole(AppRole.ADMIN)
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should stop a Manager from creating a Viewer but let an Admin")
        void shouldKeepViewerForAdminsAndAbove() {
            when(roleRepository.findHighestActiveRankByUserId(actorUserId))
                    .thenReturn(AppRole.MANAGER.rank())
                    .thenReturn(AppRole.ADMIN.rank());

            assertThatThrownBy(() -> roleHierarchyService.assertCanAssignRole(
                    restaurantActor(),
                    systemRole(AppRole.VIEWER)
            )).isInstanceOf(RoleAssignmentNotAllowedException.class);
            assertThatCode(() -> roleHierarchyService.assertCanAssignRole(
                    restaurantActor(),
                    systemRole(AppRole.VIEWER)
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should stop an Admin from editing or deleting a Manager while the switch is off")
        void shouldStopAdminManagingManagerWhenSwitchOff() {
            UUID managerUserId = UUID.randomUUID();
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.ADMIN.rank());
            when(roleRepository.findHighestActiveRankByUserId(managerUserId)).thenReturn(AppRole.MANAGER.rank());
            when(roleRepository.findActiveRolesByUserId(managerUserId)).thenReturn(List.of(systemRole(AppRole.MANAGER)));
            adminsCanManageManagers(false);

            assertThatThrownBy(() -> roleHierarchyService.assertCanManageUser(restaurantActor(), managerUserId))
                    .isInstanceOf(UserManagementNotAllowedException.class);
        }

        @Test
        @DisplayName("Should let an Admin edit or delete a Manager once the switch is on")
        void shouldLetAdminManageManagerWhenSwitchOn() {
            UUID managerUserId = UUID.randomUUID();
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.ADMIN.rank());
            when(roleRepository.findHighestActiveRankByUserId(managerUserId)).thenReturn(AppRole.MANAGER.rank());
            when(roleRepository.findActiveRolesByUserId(managerUserId)).thenReturn(List.of(systemRole(AppRole.MANAGER)));
            adminsCanManageManagers(true);

            assertThatCode(() -> roleHierarchyService.assertCanManageUser(restaurantActor(), managerUserId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should hide Manager from an Admin's assignable roles while the switch is off")
        void shouldHideManagerFromAdminAssignableRoles() {
            Role manager = systemRole(AppRole.MANAGER);
            Role waiter = systemRole(AppRole.WAITER);
            Role viewer = systemRole(AppRole.VIEWER);
            when(roleRepository.findHighestActiveRankByUserId(actorUserId)).thenReturn(AppRole.ADMIN.rank());
            when(roleRepository.findAssignableRolesForActorRank(AppRole.ADMIN.rank())).thenReturn(List.of(manager, waiter, viewer));
            adminsCanManageManagers(false);

            List<Role> result = roleHierarchyService.getAssignableRoles(restaurantActor());

            assertThat(result).containsExactly(waiter, viewer);
        }

        private Authentication restaurantActor() {
            AuthenticatedUser user = AuthenticatedUser.builder()
                    .id(actorUserId)
                    .restaurantId(restaurantId)
                    .email("user@pos.local")
                    .active(true)
                    .build();

            return new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        }

        private void adminsCanManageManagers(boolean allowed) {
            Settings settings = new Settings();
            settings.setAdminsCanManageManagers(allowed);
            when(settingsRepository.findByRestaurant_Id(restaurantId)).thenReturn(Optional.of(settings));
        }

        private Role systemRole(AppRole appRole) {
            Role role = role(appRole.name(), appRole.rank());
            role.setSystem(true);
            role.setAssignable(appRole.assignable());
            role.setProtectedRole(appRole.protectedRole());
            return role;
        }
    }

    private Authentication authentication(boolean superAdmin) {
        return authentication(UUID.randomUUID(), superAdmin);
    }

    private Authentication authentication(UUID userId, boolean superAdmin) {
        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(userId)
                .restaurantId(SHARED_RESTAURANT_ID)
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
