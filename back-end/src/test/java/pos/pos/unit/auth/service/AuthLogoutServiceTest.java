package pos.pos.unit.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthLogoutService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.service.RefreshTokenSecurityService;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthLogoutServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @InjectMocks
    private AuthLogoutService authLogoutService;

    @Test
    void logout_shouldBeIdempotentWhenSessionIsMissingAfterTokenValidation() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                new RefreshTokenSecurityService.ValidatedRefreshToken("token", "hash", tokenId, userId);

        when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
        when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());

        assertThatCode(() -> authLogoutService.logout("token")).doesNotThrowAnyException();

        verify(userSessionRepository).findByTokenId(tokenId);
        verify(userSessionRepository, never()).save(org.mockito.ArgumentMatchers.any(UserSession.class));
    }

    @Test
    void logoutAll_shouldRemainStrictWhenPresentedTokenHashDoesNotMatchStoredHash() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                new RefreshTokenSecurityService.ValidatedRefreshToken("token", "presented-hash", tokenId, userId);
        UserSession session = UserSession.builder()
                .userId(userId)
                .tokenId(tokenId)
                .refreshTokenHash("stored-hash")
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .revoked(false)
                .build();

        when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
        when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));
        when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authLogoutService.logoutAll("token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid refresh token");

        verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
