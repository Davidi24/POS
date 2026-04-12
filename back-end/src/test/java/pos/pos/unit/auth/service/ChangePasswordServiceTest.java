package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.ChangePasswordService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
@DisplayName("ChangePasswordService")
class ChangePasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private UserSessionRepository userSessionRepository;

    @InjectMocks
    private ChangePasswordService changePasswordService;

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should load the active user, update the password, and revoke other sessions")
        void shouldLoadActiveUserAndUpdatePassword() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UUID currentSessionId = UUID.randomUUID();
            ChangePasswordRequest request = request("OldSecurePass1!", "NewSecurePass1!");

            User user = User.builder()
                    .id(userId)
                    .passwordHash("old-hash")
                    .isActive(true)
                    .build();

            UserSession currentSession = UserSession.builder()
                    .id(currentSessionId)
                    .userId(userId)
                    .tokenId(tokenId)
                    .sessionType("PASSWORD")
                    .refreshTokenHash("hash")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                    .build();

            when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
            when(passwordService.matches("OldSecurePass1!", "old-hash")).thenReturn(true);
            when(passwordService.hash("NewSecurePass1!")).thenReturn("new-hash");
            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)).thenReturn(Optional.of(currentSession));

            changePasswordService.changePassword(userId, tokenId, request);

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
            assertThat(user.getPasswordUpdatedAt()).isNotNull();
            verify(userRepository).save(user);
            verify(userSessionRepository).revokeAllActiveSessionsByUserIdExcept(
                    eq(userId),
                    eq(currentSessionId),
                    any(),
                    eq(SessionRevocationReason.PASSWORD_CHANGED.name())
            );
        }

        @Test
        @DisplayName("Should throw when the current user cannot be loaded")
        void shouldThrowWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            ChangePasswordRequest request = request("OldSecurePass1!", "NewSecurePass1!");

            when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> changePasswordService.changePassword(userId, UUID.randomUUID(), request))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(passwordService, userSessionRepository);
        }

        @Test
        @DisplayName("Should reject when the current password is incorrect")
        void shouldRejectWhenCurrentPasswordIsIncorrect() {
            UUID userId = UUID.randomUUID();
            ChangePasswordRequest request = request("WrongSecurePass1!", "NewSecurePass1!");

            User user = User.builder()
                    .id(userId)
                    .passwordHash("old-hash")
                    .isActive(true)
                    .build();

            when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
            when(passwordService.matches("WrongSecurePass1!", "old-hash")).thenReturn(false);

            assertThatThrownBy(() -> changePasswordService.changePassword(userId, UUID.randomUUID(), request))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Current password is incorrect");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(userSessionRepository);
        }
    }

    private ChangePasswordRequest request(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }
}
