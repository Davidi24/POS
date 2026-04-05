package pos.pos.unit.auth.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.util.ClientInfo;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionMapperTest {

    @Test
    @DisplayName("Should map session fields and normalize client info")
    void shouldMapSessionFieldsAndNormalizeClientInfo() {
        UserSessionMapper mapper = mapperWithRefreshExpiration(Duration.ofMinutes(90));
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        UserSession session = mapper.toSession(
                userId,
                tokenId,
                "PASSWORD",
                "hashed-refresh-token",
                new ClientInfo(" 127.0.0.1 ", "  Windows NT 10.0  ")
        );

        assertThat(session.getUserId()).isEqualTo(userId);
        assertThat(session.getTokenId()).isEqualTo(tokenId);
        assertThat(session.getSessionType()).isEqualTo("PASSWORD");
        assertThat(session.getRefreshTokenHash()).isEqualTo("hashed-refresh-token");
        assertThat(session.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(session.getUserAgent()).isEqualTo("Windows NT 10.0");
        assertThat(session.getDeviceName()).isEqualTo("Windows");
        assertThat(session.isRevoked()).isFalse();
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getRevokedReason()).isNull();
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getLastUsedAt()).isEqualTo(session.getCreatedAt());
        assertThat(session.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(session.getExpiresAt()).isEqualTo(session.getCreatedAt().plusMinutes(90));
    }

    @Test
    @DisplayName("Should use UNKNOWN device and null client fields when client info is missing")
    void shouldHandleNullClientInfo() {
        UserSessionMapper mapper = mapperWithRefreshExpiration(Duration.ofDays(30));

        UserSession session = mapper.toSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PASSWORD",
                "hashed-refresh-token",
                null
        );

        assertThat(session.getIpAddress()).isNull();
        assertThat(session.getUserAgent()).isNull();
        assertThat(session.getDeviceName()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should treat blank client info values as null")
    void shouldTreatBlankClientInfoValuesAsNull() {
        UserSessionMapper mapper = mapperWithRefreshExpiration(Duration.ofDays(30));

        UserSession session = mapper.toSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PASSWORD",
                "hashed-refresh-token",
                new ClientInfo("   ", "   ")
        );

        assertThat(session.getIpAddress()).isNull();
        assertThat(session.getUserAgent()).isNull();
        assertThat(session.getDeviceName()).isEqualTo("UNKNOWN");
    }

    @Nested
    @DisplayName("Device detection")
    class DeviceDetectionTests {

        private final UserSessionMapper mapper = mapperWithRefreshExpiration(Duration.ofDays(30));

        @Test
        @DisplayName("Should detect Android before Linux")
        void shouldDetectAndroidBeforeLinux() {
            UserSession session = sessionForUserAgent("Mozilla/5.0 (Linux; Android 14)");

            assertThat(session.getDeviceName()).isEqualTo("Android");
        }

        @Test
        @DisplayName("Should detect iPhone before Mac")
        void shouldDetectIphoneBeforeMac() {
            UserSession session = sessionForUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");

            assertThat(session.getDeviceName()).isEqualTo("iPhone");
        }

        @Test
        @DisplayName("Should detect Mac")
        void shouldDetectMac() {
            UserSession session = sessionForUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)");

            assertThat(session.getDeviceName()).isEqualTo("Mac");
        }

        @Test
        @DisplayName("Should detect Linux")
        void shouldDetectLinux() {
            UserSession session = sessionForUserAgent("Mozilla/5.0 (X11; Linux x86_64)");

            assertThat(session.getDeviceName()).isEqualTo("Linux");
        }

        @Test
        @DisplayName("Should fall back to generic Device for unknown user agent")
        void shouldFallbackToGenericDevice() {
            UserSession session = sessionForUserAgent("CustomBot/1.0");

            assertThat(session.getDeviceName()).isEqualTo("Device");
        }

        private UserSession sessionForUserAgent(String userAgent) {
            return mapper.toSession(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "PASSWORD",
                    "hashed-refresh-token",
                    new ClientInfo("127.0.0.1", userAgent)
            );
        }
    }

    private UserSessionMapper mapperWithRefreshExpiration(Duration refreshExpiration) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(refreshExpiration);
        return new UserSessionMapper(jwtProperties);
    }
}
