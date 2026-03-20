package pos.pos.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-for-hs256-minimum-32b");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", Duration.ofDays(30));
        jwtService.init();
    }

    @Test
    void accessToken_shouldBeValidAndContainUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId);

        assertTrue(jwtService.isValid(token));
        assertEquals(userId, jwtService.extractUserId(token));
    }

    @Test
    void refreshToken_shouldBeValidAndContainUserAndTokenIds() {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(userId, tokenId);

        assertTrue(jwtService.isValid(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(tokenId, jwtService.extractTokenId(token));
    }

    @Test
    void isValid_shouldReturnFalseForMalformedToken() {
        assertFalse(jwtService.isValid("bad-token"));
    }
}
