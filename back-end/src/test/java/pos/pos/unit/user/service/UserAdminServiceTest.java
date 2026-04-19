package pos.pos.unit.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.PhoneAlreadyExistsException;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.dto.ReplaceUserRolesRequest;
import pos.pos.user.dto.UpdateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.user.service.UserAdminService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminService")
class UserAdminServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID WAITER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID CASHIER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID BARTENDER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserAdminService userAdminService;

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("manager@pos.local")
                        .username("manager.main")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Nested
    @DisplayName("getUsers")
    class GetUsersTests {

        @Test
        @DisplayName("Should return a paged response with role codes and requested sorting")
        void shouldReturnPagedUsers() {
            Authentication authentication = authentication();
            User user = user("cashier@pos.local", "cashier.one");
            Role waiterRole = role(WAITER_ROLE_ID, "WAITER", "Waiter", 10_000L);

            given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(false);
            given(roleHierarchyService.actorRank(authentication)).willReturn(20_000L);
            given(userRepository.searchVisibleUsers(
                    eq(true),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(false),
                    eq(20_000L),
                    any(Pageable.class)
            )).willReturn(new PageImpl<>(List.of(user)));
            given(userRoleRepository.findByUserIdIn(List.of(TARGET_USER_ID)))
                    .willReturn(List.of(userRole(TARGET_USER_ID, WAITER_ROLE_ID)));
            given(roleRepository.findByIdIn(List.of(WAITER_ROLE_ID))).willReturn(List.of(waiterRole));

            PageResponse<UserResponse> response = userAdminService.getUsers(
                    authentication,
                    null,
                    true,
                    null,
                    0,
                    10,
                    "email",
                    "asc"
            );

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            then(userRepository).should().searchVisibleUsers(
                    eq(true),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(false),
                    eq(20_000L),
                    pageableCaptor.capture()
            );

            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(0);
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort().getOrderFor("email")).isNotNull();
            assertThat(pageable.getSort().getOrderFor("email").getDirection().name()).isEqualTo("ASC");

            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getItems().get(0).getId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getItems().get(0).getRoles()).containsExactly("WAITER");
        }

        @Test
        @DisplayName("Should reject unsupported sortBy values")
        void shouldRejectUnsupportedSortBy() {
            Authentication authentication = authentication();

            assertThatThrownBy(() -> userAdminService.getUsers(
                    authentication,
                    null,
                    null,
                    null,
                    0,
                    20,
                    "passwordHash",
                    "desc"
            ))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Invalid sortBy value");
        }

        @Test
        @DisplayName("Should reject unsupported sort directions")
        void shouldRejectUnsupportedSortDirection() {
            Authentication authentication = authentication();

            assertThatThrownBy(() -> userAdminService.getUsers(
                    authentication,
                    null,
                    null,
                    null,
                    0,
                    20,
                    "createdAt",
                    "sideways"
            ))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Invalid sort direction");
        }

        @Test
        @DisplayName("Should normalize search and role filters and apply default paging")
        void shouldNormalizeFiltersAndApplyDefaults() {
            Authentication authentication = authentication();

            given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(false);
            given(roleHierarchyService.actorRank(authentication)).willReturn(20_000L);
            given(userRepository.searchVisibleUsers(
                    eq(null),
                    eq("%+1 (555) 0200%"),
                    eq("%+15550200%"),
                    eq("WAITER"),
                    eq(false),
                    eq(20_000L),
                    any(Pageable.class)
            )).willReturn(Page.empty());

            PageResponse<UserResponse> response = userAdminService.getUsers(
                    authentication,
                    " +1 (555) 0200 ",
                    null,
                    " waiter ",
                    null,
                    null,
                    null,
                    null
            );

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            then(userRepository).should().searchVisibleUsers(
                    eq(null),
                    eq("%+1 (555) 0200%"),
                    eq("%+15550200%"),
                    eq("WAITER"),
                    eq(false),
                    eq(20_000L),
                    pageableCaptor.capture()
            );

            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(0);
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
            assertThat(pageable.getSort().getOrderFor("createdAt").getDirection().name()).isEqualTo("DESC");
            assertThat(response.getItems()).isEmpty();
        }
    }

    @Test
    @DisplayName("getUser should return the target user with active role codes")
    void getUserShouldReturnTargetUser() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(roleRepository.findActiveRoleCodesByUserId(TARGET_USER_ID)).willReturn(List.of("WAITER"));

        UserResponse response = userAdminService.getUser(authentication, TARGET_USER_ID);

        verify(roleHierarchyService).assertCanManageUser(authentication, TARGET_USER_ID);
        assertThat(response.getUsername()).isEqualTo("cashier.one");
        assertThat(response.getRoles()).containsExactly("WAITER");
    }

    @Test
    @DisplayName("getUser should reject unknown users")
    void getUserShouldRejectMissingUser() {
        Authentication authentication = authentication();
        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> userAdminService.getUser(authentication, TARGET_USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("updateUser should update fields, reset phone verification, and revoke sessions when deactivated")
    void updateUserShouldResetPhoneVerificationAndRevokeSessions() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        user.setPhone("+15550100");
        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(OffsetDateTime.now());

        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+1 555 0200")
                .isActive(false)
                .build();

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(userRepository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull("+15550200", TARGET_USER_ID)).willReturn(false);
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(roleRepository.findActiveRoleCodesByUserId(TARGET_USER_ID)).willReturn(List.of("WAITER"));

        UserResponse response = userAdminService.updateUser(authentication, TARGET_USER_ID, request);

        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getPhone()).isEqualTo("+1 555 0200");
        assertThat(response.getIsActive()).isFalse();
        assertThat(user.getStatus()).isEqualTo("INACTIVE");
        assertThat(user.isPhoneVerified()).isFalse();
        assertThat(user.getPhoneVerifiedAt()).isNull();
        assertThat(user.getUpdatedBy()).isEqualTo(ACTOR_ID);

        verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                eq(TARGET_USER_ID),
                any(OffsetDateTime.class),
                eq(SessionRevocationReason.SESSION_REVOKED.name())
        );
    }

    @Test
    @DisplayName("updateUser should reject duplicate phones owned by another active user")
    void updateUserShouldRejectDuplicatePhone() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");

        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+1 555 0200")
                .isActive(true)
                .build();

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(userRepository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull("+15550200", TARGET_USER_ID)).willReturn(true);

        assertThatThrownBy(() -> userAdminService.updateUser(authentication, TARGET_USER_ID, request))
                .isInstanceOf(PhoneAlreadyExistsException.class)
                .hasMessage("Phone already in use");

        verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(UUID.class), any(OffsetDateTime.class), anyString());
    }

    @Test
    @DisplayName("updateUser should keep phone verification when normalized phone is unchanged")
    void updateUserShouldKeepPhoneVerificationWhenNormalizedPhoneIsUnchanged() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        user.setPhone(" +1 555 0100 ");
        user.setNormalizedPhone("+15550100");
        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(OffsetDateTime.now());
        user.setActive(false);
        user.setStatus("INACTIVE");

        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+1-555-0100")
                .isActive(false)
                .build();

        OffsetDateTime verifiedAt = user.getPhoneVerifiedAt();

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(roleRepository.findActiveRoleCodesByUserId(TARGET_USER_ID)).willReturn(List.of("WAITER"));

        UserResponse response = userAdminService.updateUser(authentication, TARGET_USER_ID, request);

        assertThat(response.getIsActive()).isFalse();
        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.getPhoneVerifiedAt()).isEqualTo(verifiedAt);
        verify(userRepository, never()).existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull(anyString(), eq(TARGET_USER_ID));
        verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(UUID.class), any(OffsetDateTime.class), anyString());
    }

    @Test
    @DisplayName("updateUser should stop when actor cannot manage the target user")
    void updateUserShouldStopWhenActorCannotManageUser() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+1 555 0200")
                .isActive(true)
                .build();

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        doThrow(new UserManagementNotAllowedException())
                .when(roleHierarchyService).assertCanManageUser(authentication, TARGET_USER_ID);

        assertThatThrownBy(() -> userAdminService.updateUser(authentication, TARGET_USER_ID, request))
                .isInstanceOf(UserManagementNotAllowedException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(UUID.class), any(OffsetDateTime.class), anyString());
    }

    @Test
    @DisplayName("getUserRoles should map active roles to API responses")
    void getUserRolesShouldMapActiveRoles() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        Role waiterRole = role(WAITER_ROLE_ID, "WAITER", "Waiter", 10_000L);
        Role cashierRole = role(CASHIER_ROLE_ID, "CASHIER", "Cashier", 9_000L);

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(roleRepository.findActiveRolesByUserId(TARGET_USER_ID)).willReturn(List.of(waiterRole, cashierRole));

        List<RoleResponse> response = userAdminService.getUserRoles(authentication, TARGET_USER_ID);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getCode()).isEqualTo("WAITER");
        assertThat(response.get(1).getCode()).isEqualTo("CASHIER");
    }

    @Test
    @DisplayName("getUserRoles should reject unknown users")
    void getUserRolesShouldRejectMissingUser() {
        Authentication authentication = authentication();
        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> userAdminService.getUserRoles(authentication, TARGET_USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("replaceUserRoles should remove dropped roles, keep unchanged roles, and add new ones")
    void replaceUserRolesShouldDiffAssignments() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        Role cashierRole = role(CASHIER_ROLE_ID, "CASHIER", "Cashier", 9_000L);
        Role bartenderRole = role(BARTENDER_ROLE_ID, "BARTENDER", "Bartender", 8_000L);

        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest();
        request.setRoleIds(Set.of(CASHIER_ROLE_ID, BARTENDER_ROLE_ID));

        UserRole waiterAssignment = userRole(TARGET_USER_ID, WAITER_ROLE_ID);
        UserRole cashierAssignment = userRole(TARGET_USER_ID, CASHIER_ROLE_ID);

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID))
                .willReturn(java.util.Optional.of(user), java.util.Optional.of(user));
        given(roleRepository.findByIdIn(any()))
                .willReturn(List.of(cashierRole, bartenderRole));
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(userRoleRepository.findByUserId(TARGET_USER_ID)).willReturn(List.of(waiterAssignment, cashierAssignment));

        UserResponse response = userAdminService.replaceUserRoles(authentication, TARGET_USER_ID, request);

        verify(roleHierarchyService).assertCanManageUser(authentication, TARGET_USER_ID);
        verify(roleHierarchyService).assertCanAssignRole(authentication, cashierRole);
        verify(roleHierarchyService).assertCanAssignRole(authentication, bartenderRole);
        verify(userRoleRepository).deleteAll(List.of(waiterAssignment));
        verify(userRoleRepository).saveAll(List.of(UserRole.builder()
                .userId(TARGET_USER_ID)
                .roleId(BARTENDER_ROLE_ID)
                .assignedBy(ACTOR_ID)
                .build()));
        assertThat(response.getRoles()).containsExactly("CASHIER", "BARTENDER");
    }

    @Test
    @DisplayName("replaceUserRoles should reject missing or inactive roles")
    void replaceUserRolesShouldRejectMissingOrInactiveRoles() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest();
        request.setRoleIds(Set.of(CASHIER_ROLE_ID, BARTENDER_ROLE_ID));

        Role cashierRole = role(CASHIER_ROLE_ID, "CASHIER", "Cashier", 9_000L);
        Role inactiveRole = role(BARTENDER_ROLE_ID, "BARTENDER", "Bartender", 8_000L);
        inactiveRole.setActive(false);

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(roleRepository.findByIdIn(any())).willReturn(List.of(cashierRole, inactiveRole));

        assertThatThrownBy(() -> userAdminService.replaceUserRoles(authentication, TARGET_USER_ID, request))
                .isInstanceOf(RoleNotFoundException.class);

        verify(userRoleRepository, never()).deleteAll(any());
        verify(userRoleRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("replaceUserRoles should stop when one requested role cannot be assigned")
    void replaceUserRolesShouldStopWhenAssignmentIsNotAllowed() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest();
        request.setRoleIds(Set.of(CASHIER_ROLE_ID));
        Role cashierRole = role(CASHIER_ROLE_ID, "CASHIER", "Cashier", 9_000L);

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(roleRepository.findByIdIn(any())).willReturn(List.of(cashierRole));
        doThrow(new RoleAssignmentNotAllowedException())
                .when(roleHierarchyService).assertCanAssignRole(authentication, cashierRole);

        assertThatThrownBy(() -> userAdminService.replaceUserRoles(authentication, TARGET_USER_ID, request))
                .isInstanceOf(RoleAssignmentNotAllowedException.class);

        verify(userRoleRepository, never()).deleteAll(any());
        verify(userRoleRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("replaceUserRoles should avoid writes when assignments already match")
    void replaceUserRolesShouldAvoidWritesWhenAssignmentsAlreadyMatch() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");
        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest();
        request.setRoleIds(Set.of(CASHIER_ROLE_ID));
        Role cashierRole = role(CASHIER_ROLE_ID, "CASHIER", "Cashier", 9_000L);

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID))
                .willReturn(java.util.Optional.of(user), java.util.Optional.of(user));
        given(roleRepository.findByIdIn(any())).willReturn(List.of(cashierRole));
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(userRoleRepository.findByUserId(TARGET_USER_ID))
                .willReturn(List.of(userRole(TARGET_USER_ID, CASHIER_ROLE_ID)));

        UserResponse response = userAdminService.replaceUserRoles(authentication, TARGET_USER_ID, request);

        assertThat(response.getRoles()).containsExactly("CASHIER");
        verify(userRoleRepository, never()).deleteAll(any());
        verify(userRoleRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("deleteUser should soft delete the user and revoke active sessions")
    void deleteUserShouldSoftDeleteAndRevokeSessions() {
        Authentication authentication = authentication();
        User user = user("cashier@pos.local", "cashier.one");

        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.of(user));
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);

        userAdminService.deleteUser(authentication, TARGET_USER_ID);

        assertThat(user.isActive()).isFalse();
        assertThat(user.getStatus()).isEqualTo("DELETED");
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getUpdatedBy()).isEqualTo(ACTOR_ID);
        verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                eq(TARGET_USER_ID),
                any(OffsetDateTime.class),
                eq(SessionRevocationReason.SESSION_REVOKED.name())
        );
    }

    @Test
    @DisplayName("deleteUser should reject unknown users")
    void deleteUserShouldRejectMissingUser() {
        Authentication authentication = authentication();
        given(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> userAdminService.deleteUser(authentication, TARGET_USER_ID))
                .isInstanceOf(UserNotFoundException.class);

        verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(UUID.class), any(OffsetDateTime.class), anyString());
    }

    private User user(String email, String username) {
        return User.builder()
                .id(TARGET_USER_ID)
                .email(email)
                .username(username)
                .passwordHash("stored-hash")
                .firstName("John")
                .lastName("Doe")
                .phone("+15550100")
                .status("ACTIVE")
                .isActive(true)
                .normalizedPhone("+15550100")
                .build();
    }

    private Role role(UUID id, String code, String name, long rank) {
        return Role.builder()
                .id(id)
                .code(code)
                .name(name)
                .rank(rank)
                .isActive(true)
                .assignable(true)
                .build();
    }

    private UserRole userRole(UUID userId, UUID roleId) {
        return UserRole.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .roleId(roleId)
                .assignedBy(ACTOR_ID)
                .build();
    }
}
