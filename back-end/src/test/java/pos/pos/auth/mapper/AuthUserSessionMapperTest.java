package pos.pos.auth.mapper;

import org.junit.jupiter.api.Test;
import pos.pos.security.config.JwtProperties;
import pos.pos.auth.entity.UserSession;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthUserSessionMapperTest {

    @Test
    void toSession_shouldMapFieldsAndApplyRefreshExpiration() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshExpiration(Duration.ofDays(30));
        UserSessionMapper mapper = new UserSessionMapper(properties);

        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();

        UserSession session = mapper.toSession(userId, tokenId, "hash", "127.0.0.1", "JUnit");

        OffsetDateTime after = OffsetDateTime.now();

        assertEquals(userId, session.getUserId());
        assertEquals(tokenId, session.getTokenId());
        assertEquals("hash", session.getRefreshTokenHash());
        assertEquals("127.0.0.1", session.getIpAddress());
        assertEquals("JUnit", session.getUserAgent());
        assertFalse(session.getRevoked());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastUsedAt());
        assertNotNull(session.getExpiresAt());
        assertTrue(!session.getCreatedAt().isBefore(before) && !session.getCreatedAt().isAfter(after));
        assertTrue(session.getExpiresAt().isAfter(session.getCreatedAt()));
    }
}
