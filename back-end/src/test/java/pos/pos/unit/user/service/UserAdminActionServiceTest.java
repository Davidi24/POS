package pos.pos.unit.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.RecoveryChannel;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.auth.service.PhoneVerificationService;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.dto.AdminPasswordResetRequest;
import pos.pos.user.dto.ClientTargetRequest;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.service.UserAdminActionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminActionService")
class UserAdminActionServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000241");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000242");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PhoneVerificationService phoneVerificationService;

    @InjectMocks
    private UserAdminActionService userAdminActionService;

    @Test
    @DisplayName("Should default password reset requests to email and the universal client target")
    void shouldDefaultPasswordResetRequestsToEmailAndUniversalTarget() {
        Authentication authentication = authentication();
        User user = activeUser();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        userAdminActionService.requestPasswordReset(authentication, USER_ID, null);

        verify(roleHierarchyService).assertCanManageUser(authentication, USER_ID);
        verify(passwordResetService).issueAdminReset(user, RecoveryChannel.EMAIL, ClientLinkTarget.UNIVERSAL);
    }

    @Test
    @DisplayName("Should forward explicit password reset channel and client target")
    void shouldForwardExplicitPasswordResetChannelAndClientTarget() {
        Authentication authentication = authentication();
        User user = activeUser();
        AdminPasswordResetRequest request = new AdminPasswordResetRequest();
        request.setChannel(RecoveryChannel.SMS);
        request.setClientTarget(ClientLinkTarget.MOBILE);

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        userAdminActionService.requestPasswordReset(authentication, USER_ID, request);

        verify(passwordResetService).issueAdminReset(user, RecoveryChannel.SMS, ClientLinkTarget.MOBILE);
    }

    @Test
    @DisplayName("Should resend verification email for manageable users")
    void shouldResendVerificationEmailForManageableUsers() {
        Authentication authentication = authentication();
        User user = activeUser();
        ClientTargetRequest request = new ClientTargetRequest();
        request.setClientTarget(ClientLinkTarget.WEB);

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        userAdminActionService.sendVerificationEmail(authentication, USER_ID, request);

        verify(emailVerificationService).issueVerificationForUser(user, ClientLinkTarget.WEB);
    }

    @Test
    @DisplayName("Should reject verification email requests for already verified users")
    void shouldRejectVerificationEmailRequestsForAlreadyVerifiedUsers() {
        Authentication authentication = authentication();
        User user = activeUser();
        user.setEmailVerified(true);

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userAdminActionService.sendVerificationEmail(authentication, USER_ID, new ClientTargetRequest()))
                .isInstanceOf(AuthException.class)
                .hasMessage("User email is already verified");

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    @DisplayName("Should request phone verification for manageable users")
    void shouldRequestPhoneVerificationForManageableUsers() {
        Authentication authentication = authentication();
        User user = activeUser();
        user.setPhone("+49 555 01-00");
        user.setNormalizedPhone("+495550100");

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        userAdminActionService.sendPhoneVerification(authentication, USER_ID);

        verify(phoneVerificationService).requestPhoneVerification(USER_ID);
    }

    @Test
    @DisplayName("Should reject missing users before calling downstream services")
    void shouldRejectMissingUsersBeforeCallingDownstreamServices() {
        Authentication authentication = authentication();

        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminActionService.requestPasswordReset(authentication, USER_ID, new AdminPasswordResetRequest()))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(passwordResetService, emailVerificationService, phoneVerificationService);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("manager@pos.local")
                        .username("manager")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    private User activeUser() {
        return User.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .passwordHash("stored-hash")
                .firstName("Casey")
                .lastName("Cashier")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }
}
