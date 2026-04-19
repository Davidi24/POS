package pos.pos.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.RecoveryChannel;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.auth.service.PhoneVerificationService;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.dto.AdminPasswordResetRequest;
import pos.pos.user.dto.ClientTargetRequest;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminActionService {

    private final UserRepository userRepository;
    private final RoleHierarchyService roleHierarchyService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final PhoneVerificationService phoneVerificationService;

    public void requestPasswordReset(Authentication authentication, UUID userId, AdminPasswordResetRequest request) {
        User user = findManageableUser(authentication, userId);
        assertActiveUser(user, "User account must be active before sending a password reset");
        passwordResetService.issueAdminReset(
                user,
                request == null || request.getChannel() == null ? RecoveryChannel.EMAIL : request.getChannel(),
                request == null || request.getClientTarget() == null ? ClientLinkTarget.UNIVERSAL : request.getClientTarget()
        );
    }

    public void sendVerificationEmail(Authentication authentication, UUID userId, ClientTargetRequest request) {
        User user = findManageableUser(authentication, userId);
        assertActiveUser(user, "User account must be active before sending a verification email");
        if (user.isEmailVerified()) {
            throw new AuthException("User email is already verified", HttpStatus.BAD_REQUEST);
        }

        emailVerificationService.issueVerificationForUser(
                user,
                request == null || request.getClientTarget() == null ? ClientLinkTarget.UNIVERSAL : request.getClientTarget()
        );
    }

    public void sendPhoneVerification(Authentication authentication, UUID userId) {
        User user = findManageableUser(authentication, userId);
        assertActiveUser(user, "User account must be active before sending a phone verification code");
        if (user.isPhoneVerified()) {
            throw new AuthException("User phone is already verified", HttpStatus.BAD_REQUEST);
        }

        phoneVerificationService.requestPhoneVerification(user.getId());
    }

    private User findManageableUser(Authentication authentication, UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(UserNotFoundException::new);
        roleHierarchyService.assertCanManageUser(authentication, userId);
        return user;
    }

    private void assertActiveUser(User user, String message) {
        if (!user.isActive()) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }
}
