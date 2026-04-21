package pos.pos.unit.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.RefreshTokenSecurityService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenSecurityService")
class RefreshTokenSecurityServiceTest {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String PEPPER = "refresh-token-pepper-super-secret-32ch";

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenSecurityService, "refreshTokenPepper", PEPPER);
    }

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("Should return normalized token, hash, and claims for a valid refresh token")
        void shouldReturnValidatedRefreshToken_whenTokenIsValid() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
            when(jwtService.extractTokenId("refresh-token")).thenReturn(tokenId);
            when(jwtService.extractUserId("refresh-token")).thenReturn(userId);

            RefreshTokenSecurityService.ValidatedRefreshToken validated =
                    refreshTokenSecurityService.validate("  refresh-token  ");

            assertThat(validated.token()).isEqualTo("refresh-token");
            assertThat(validated.tokenHash()).isEqualTo(expectedHash("refresh-token"));
            assertThat(validated.tokenId()).isEqualTo(tokenId);
            assertThat(validated.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should reject blank tokens before calling JwtService")
        void shouldRejectBlankToken() {
            assertThatThrownBy(() -> refreshTokenSecurityService.validate("   "))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Should reject null tokens before calling JwtService")
        void shouldRejectNullToken() {
            assertThatThrownBy(() -> refreshTokenSecurityService.validate(null))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Should reject tokens that are not refresh tokens")
        void shouldRejectNonRefreshToken() {
            when(jwtService.isRefreshToken("access-token")).thenReturn(false);

            assertThatThrownBy(() -> refreshTokenSecurityService.validate("access-token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Should reject malformed token claims")
        void shouldRejectMalformedClaims() {
            when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
            when(jwtService.extractTokenId("refresh-token")).thenThrow(new IllegalArgumentException("bad token id"));

            assertThatThrownBy(() -> refreshTokenSecurityService.validate("refresh-token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(jwtService, never()).extractUserId(anyString());
        }
    }

    @Nested
    @DisplayName("hash()")
    class HashTests {

        @Test
        @DisplayName("Should normalize token before hashing")
        void shouldNormalizeBeforeHashing() {
            assertThat(refreshTokenSecurityService.hash("  refresh-token  "))
                    .isEqualTo(expectedHash("refresh-token"))
                    .isEqualTo(refreshTokenSecurityService.hash("refresh-token"));
        }

        @Test
        @DisplayName("Should reject null tokens when hashing")
        void shouldRejectNullToken_whenHashing() {
            assertThatThrownBy(() -> refreshTokenSecurityService.hash(null))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    @Nested
    @DisplayName("matchesHash()")
    class MatchesHashTests {

        @Test
        @DisplayName("Should return true when hashes match")
        void shouldReturnTrue_whenHashesMatch() {
            RefreshTokenSecurityService.ValidatedRefreshToken validated =
                    new RefreshTokenSecurityService.ValidatedRefreshToken(
                            "refresh-token",
                            expectedHash("refresh-token"),
                            UUID.randomUUID(),
                            UUID.randomUUID()
                    );

            assertThat(refreshTokenSecurityService.matchesHash(validated, expectedHash("refresh-token"))).isTrue();
        }

        @Test
        @DisplayName("Should return false when stored hash is null")
        void shouldReturnFalse_whenStoredHashNull() {
            RefreshTokenSecurityService.ValidatedRefreshToken validated =
                    new RefreshTokenSecurityService.ValidatedRefreshToken(
                            "refresh-token",
                            expectedHash("refresh-token"),
                            UUID.randomUUID(),
                            UUID.randomUUID()
                    );

            assertThat(refreshTokenSecurityService.matchesHash(validated, null)).isFalse();
        }

        @Test
        @DisplayName("Should return false when hashes differ")
        void shouldReturnFalse_whenHashesDiffer() {
            RefreshTokenSecurityService.ValidatedRefreshToken validated =
                    new RefreshTokenSecurityService.ValidatedRefreshToken(
                            "refresh-token",
                            expectedHash("refresh-token"),
                            UUID.randomUUID(),
                            UUID.randomUUID()
                    );

            assertThat(refreshTokenSecurityService.matchesHash(validated, expectedHash("other-token"))).isFalse();
        }
    }

    @Nested
    @DisplayName("configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should fail fast when refresh token pepper is blank")
        void shouldFailFast_whenPepperBlank() {
            ReflectionTestUtils.setField(refreshTokenSecurityService, "refreshTokenPepper", "   ");

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(refreshTokenSecurityService, "validateConfiguration"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("app.security.refresh-token.pepper must not be blank");
        }

        @Test
        @DisplayName("Should fail fast when refresh token pepper is shorter than 32 characters")
        void shouldFailFast_whenPepperTooShort() {
            ReflectionTestUtils.setField(refreshTokenSecurityService, "refreshTokenPepper", "short-pepper");

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(refreshTokenSecurityService, "validateConfiguration"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("app.security.refresh-token.pepper must be at least 32 characters");
        }
    }

    private String expectedHash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((refreshToken + PEPPER).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
