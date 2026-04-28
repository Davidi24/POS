package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.service.AuthRegisterService;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.exception.auth.PhoneAlreadyExistsException;
import pos.pos.exception.auth.UsernameAlreadyExistsException;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.dto.CreateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.user.service.UserIdentityService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthRegisterService")
class AuthRegisterServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private UserIdentityService userIdentityService;

    @InjectMocks
    private AuthRegisterService authRegisterService;

    @Test
    @DisplayName("Should register a user with normalized identifiers and issue email verification")
    void shouldRegisterUserAndIssueVerification() {
        UUID actorUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID createdUserId = UUID.randomUUID();
        Authentication authentication = authentication(actorUserId);
        CreateUserRequest request = validRequest();
        request.setEmail("  Cashier@POS.local  ");
        request.setUsername("  Cashier.One  ");
        request.setPhone(" +49 555 0100 ");
        request.setClientTarget(ClientLinkTarget.MOBILE);
        request.setRoleId(roleId);

        Role role = Role.builder()
                .id(roleId)
                .code("MANAGER")
                .name("Manager")
                .description("Store manager")
                .rank(20_000L)
                .isActive(true)
                .assignable(true)
                .build();
        UserResponse mappedResponse = UserResponse.builder()
                .id(createdUserId)
                .email("cashier@pos.local")
                .username("cashier.one")
                .roles(List.of("MANAGER"))
                .build();

        when(userIdentityService.normalizeAndAssertUnique("  Cashier@POS.local  ", "  Cashier.One  ", " +49 555 0100 "))
                .thenReturn(new UserIdentityService.NormalizedUserIdentity("cashier@pos.local", "cashier.one", "+495550100"));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(passwordService.hash("SecurePass1!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(createdUserId);
            return user;
        });
        when(userMapper.toUserResponse(any(User.class), eq(List.of("MANAGER")))).thenReturn(mappedResponse);

        UserResponse response = authRegisterService.register(request, authentication);

        assertThat(response).isSameAs(mappedResponse);
        verify(roleHierarchyService).assertCanAssignRole(authentication, role);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("cashier@pos.local");
        assertThat(savedUser.getUsername()).isEqualTo("cashier.one");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getFirstName()).isEqualTo("John");
        assertThat(savedUser.getLastName()).isEqualTo("Doe");
        assertThat(savedUser.getPhone()).isEqualTo(" +49 555 0100 ");
        assertThat(savedUser.getCreatedBy()).isEqualTo(actorUserId);
        assertThat(savedUser.getUpdatedBy()).isEqualTo(actorUserId);
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.isPhoneVerified()).isFalse();

        ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(userRoleCaptor.capture());
        UserRole savedUserRole = userRoleCaptor.getValue();
        assertThat(savedUserRole.getUserId()).isEqualTo(createdUserId);
        assertThat(savedUserRole.getRoleId()).isEqualTo(roleId);
        assertThat(savedUserRole.getAssignedBy()).isEqualTo(actorUserId);

        verify(emailVerificationService).issueVerificationForUser(savedUser, ClientLinkTarget.MOBILE);
        verify(userMapper).toUserResponse(savedUser, List.of("MANAGER"));
    }

    @Test
    @DisplayName("Should reject registration when email already exists")
    void shouldRejectWhenEmailAlreadyExists() {
        CreateUserRequest request = validRequest();

        when(userIdentityService.normalizeAndAssertUnique("cashier@pos.local", "cashier.one", "+49 555 0100"))
                .thenThrow(new EmailAlreadyExistsException());

        assertThatThrownBy(() -> authRegisterService.register(request, authentication(UUID.randomUUID())))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, userRoleRepository, passwordService, userMapper, roleHierarchyService, emailVerificationService);
    }

    @Test
    @DisplayName("Should reject registration when username already exists")
    void shouldRejectWhenUsernameAlreadyExists() {
        CreateUserRequest request = validRequest();

        when(userIdentityService.normalizeAndAssertUnique("cashier@pos.local", "cashier.one", "+49 555 0100"))
                .thenThrow(new UsernameAlreadyExistsException());

        assertThatThrownBy(() -> authRegisterService.register(request, authentication(UUID.randomUUID())))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, userRoleRepository, passwordService, userMapper, roleHierarchyService, emailVerificationService);
    }

    @Test
    @DisplayName("Should reject registration when normalized phone already exists")
    void shouldRejectWhenPhoneAlreadyExists() {
        CreateUserRequest request = validRequest();

        when(userIdentityService.normalizeAndAssertUnique("cashier@pos.local", "cashier.one", "+49 555 0100"))
                .thenThrow(new PhoneAlreadyExistsException());

        assertThatThrownBy(() -> authRegisterService.register(request, authentication(UUID.randomUUID())))
                .isInstanceOf(PhoneAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, userRoleRepository, passwordService, userMapper, roleHierarchyService, emailVerificationService);
    }

    @Test
    @DisplayName("Should reject registration when target role is missing or inactive")
    void shouldRejectWhenRoleMissingOrInactive() {
        UUID roleId = UUID.randomUUID();
        CreateUserRequest request = validRequest();
        request.setRoleId(roleId);

        when(userIdentityService.normalizeAndAssertUnique("cashier@pos.local", "cashier.one", "+49 555 0100"))
                .thenReturn(new UserIdentityService.NormalizedUserIdentity("cashier@pos.local", "cashier.one", "+495550100"));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(
                Role.builder()
                        .id(roleId)
                        .code("MANAGER")
                        .name("Manager")
                        .isActive(false)
                        .build()
        ));

        assertThatThrownBy(() -> authRegisterService.register(request, authentication(UUID.randomUUID())))
                .isInstanceOf(RoleNotFoundException.class);

        verifyNoInteractions(passwordService, userMapper, roleHierarchyService, emailVerificationService);
        verify(userRepository, never()).save(any(User.class));
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    @DisplayName("Should stop registration when role assignment is not allowed")
    void shouldStopWhenRoleAssignmentIsNotAllowed() {
        UUID roleId = UUID.randomUUID();
        CreateUserRequest request = validRequest();
        request.setRoleId(roleId);
        Authentication authentication = authentication(UUID.randomUUID());
        Role role = Role.builder()
                .id(roleId)
                .code("OWNER")
                .name("Owner")
                .rank(50_000L)
                .isActive(true)
                .assignable(false)
                .protectedRole(true)
                .build();

        when(userIdentityService.normalizeAndAssertUnique("cashier@pos.local", "cashier.one", "+49 555 0100"))
                .thenReturn(new UserIdentityService.NormalizedUserIdentity("cashier@pos.local", "cashier.one", "+495550100"));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        org.mockito.Mockito.doThrow(new RoleAssignmentNotAllowedException())
                .when(roleHierarchyService).assertCanAssignRole(authentication, role);

        assertThatThrownBy(() -> authRegisterService.register(request, authentication))
                .isInstanceOf(RoleAssignmentNotAllowedException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(userRoleRepository, never()).save(any(UserRole.class));
        verifyNoInteractions(passwordService, userMapper, emailVerificationService);
    }

    private Authentication authentication(UUID actorUserId) {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(actorUserId)
                        .email("manager@pos.local")
                        .username("manager")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    private CreateUserRequest validRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("cashier@pos.local");
        request.setUsername("cashier.one");
        request.setTemporaryPassword("SecurePass1!");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPhone("+49 555 0100");
        request.setRoleId(UUID.randomUUID());
        return request;
    }
}
