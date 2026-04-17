package pos.pos.unit.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.security.service.JwtService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "jwt-secret-key-must-be-at-least-32-bytes";
    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(15);
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(7);

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = configuredService(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Nested
    @DisplayName("configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should fail fast when secret is blank")
        void shouldFailFastWhenSecretBlank() {
            JwtService service = new JwtService();
            ReflectionTestUtils.setField(service, "secret", "   ");
            ReflectionTestUtils.setField(service, "accessTokenExpiration", ACCESS_EXPIRATION);
            ReflectionTestUtils.setField(service, "refreshTokenExpiration", REFRESH_EXPIRATION);

            assertThatThrownBy(service::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("security.jwt.secret must not be blank");
        }

        @Test
        @DisplayName("Should fail fast when secret is too short")
        void shouldFailFastWhenSecretTooShort() {
            JwtService service = new JwtService();
            ReflectionTestUtils.setField(service, "secret", "too-short-secret");
            ReflectionTestUtils.setField(service, "accessTokenExpiration", ACCESS_EXPIRATION);
            ReflectionTestUtils.setField(service, "refreshTokenExpiration", REFRESH_EXPIRATION);

            assertThatThrownBy(service::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("security.jwt.secret must be at least 32 bytes for HS256");
        }

        @Test
        @DisplayName("Should fail fast when access expiration is invalid")
        void shouldFailFastWhenAccessExpirationInvalid() {
            JwtService service = new JwtService();
            ReflectionTestUtils.setField(service, "secret", SECRET);
            ReflectionTestUtils.setField(service, "accessTokenExpiration", null);
            ReflectionTestUtils.setField(service, "refreshTokenExpiration", REFRESH_EXPIRATION);

            assertThatThrownBy(service::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("security.jwt.access-expiration must be positive");
        }

        @Test
        @DisplayName("Should fail fast when refresh expiration is invalid")
        void shouldFailFastWhenRefreshExpirationInvalid() {
            JwtService service = new JwtService();
            ReflectionTestUtils.setField(service, "secret", SECRET);
            ReflectionTestUtils.setField(service, "accessTokenExpiration", ACCESS_EXPIRATION);
            ReflectionTestUtils.setField(service, "refreshTokenExpiration", Duration.ZERO);

            assertThatThrownBy(service::init)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("security.jwt.refresh-expiration must be positive");
        }
    }

    @Nested
    @DisplayName("access token")
    class AccessTokenTests {

        @Test
        @DisplayName("Should generate a valid access token with expected claims")
        void shouldGenerateValidAccessToken() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            List<String> roles = List.of("ADMIN", "CASHIER");

            String token = jwtService.generateAccessToken(userId, roles, tokenId);
            Claims claims = parseClaims(token);

            assertThat(jwtService.isValid(token)).isTrue();
            assertThat(jwtService.isAccessToken(token)).isTrue();
            assertThat(jwtService.isRefreshToken(token)).isFalse();
            assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
            assertThat(jwtService.extractTokenId(token)).isEqualTo(tokenId);
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.getId()).isEqualTo(tokenId.toString());
            assertThat(claims.get("type", String.class)).isEqualTo("access");
            assertThat(extractRoles(claims)).containsExactlyElementsOf(roles);
            assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(claims.getExpiration());
            assertThat(jwtService.getAccessTokenExpiration()).isEqualTo(ACCESS_EXPIRATION);
        }
    }

    @Nested
    @DisplayName("refresh token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should generate a valid refresh token with expected claims")
        void shouldGenerateValidRefreshToken() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();

            String token = jwtService.generateRefreshToken(userId, tokenId);
            Claims claims = parseClaims(token);

            assertThat(jwtService.isValid(token)).isTrue();
            assertThat(jwtService.isRefreshToken(token)).isTrue();
            assertThat(jwtService.isAccessToken(token)).isFalse();
            assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
            assertThat(jwtService.extractTokenId(token)).isEqualTo(tokenId);
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.getId()).isEqualTo(tokenId.toString());
            assertThat(claims.get("type", String.class)).isEqualTo("refresh");
            assertThat(claims.get("roles")).isNull();
        }
    }

    @Nested
    @DisplayName("validation")
    class ValidationTests {

        @Test
        @DisplayName("Should return false for null and blank tokens")
        void shouldReturnFalseForNullAndBlankTokens() {
            assertThat(jwtService.isValid(null)).isFalse();
            assertThat(jwtService.isValid("   ")).isFalse();
            assertThat(jwtService.isAccessToken(null)).isFalse();
            assertThat(jwtService.isAccessToken("   ")).isFalse();
            assertThat(jwtService.isRefreshToken(null)).isFalse();
            assertThat(jwtService.isRefreshToken("   ")).isFalse();
        }

        @Test
        @DisplayName("Should reject access token when refresh token is expected")
        void shouldRejectAccessTokenWhenRefreshTokenIsExpected() {
            String accessToken = jwtService.generateAccessToken(UUID.randomUUID(), List.of("ADMIN"), UUID.randomUUID());
            String refreshToken = jwtService.generateRefreshToken(UUID.randomUUID(), UUID.randomUUID());

            assertThat(jwtService.isRefreshToken(accessToken)).isFalse();
            assertThat(jwtService.isAccessToken(refreshToken)).isFalse();
        }

        @Test
        @DisplayName("Should reject tampered tokens")
        void shouldRejectTamperedToken() {
            String token = jwtService.generateRefreshToken(UUID.randomUUID(), UUID.randomUUID());
            String[] parts = token.split("\\.");
            String signature = parts[2];
            String tamperedSignature = (signature.charAt(0) == 'a' ? "b" : "a") + signature.substring(1);
            String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

            assertThat(jwtService.isValid(tampered)).isFalse();
            assertThat(jwtService.isRefreshToken(tampered)).isFalse();
            assertThat(jwtService.isAccessToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("Should reject expired tokens")
        void shouldRejectExpiredToken() throws InterruptedException {
            JwtService shortLivedService = configuredService(SECRET, Duration.ofMillis(50), Duration.ofMillis(50));
            String token = shortLivedService.generateAccessToken(UUID.randomUUID(), List.of("ADMIN"), UUID.randomUUID());

            Thread.sleep(150);

            assertThat(shortLivedService.isValid(token)).isFalse();
            assertThat(shortLivedService.isAccessToken(token)).isFalse();
            assertThat(shortLivedService.isRefreshToken(token)).isFalse();
            assertThatThrownBy(() -> shortLivedService.extractUserId(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }
    }

    private JwtService configuredService(String secret, Duration accessExpiration, Duration refreshExpiration) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", secret);
        ReflectionTestUtils.setField(service, "accessTokenExpiration", accessExpiration);
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", refreshExpiration);
        service.init();
        return service;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        return (List<String>) claims.get("roles");
    }
}
