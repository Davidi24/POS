package pos.pos.auth.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.auth.entity.UserSession;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSessionRepositoryTest {

    @Autowired
    private UserSessionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Nested
    @DisplayName("findByTokenId")
    class FindByTokenIdTests {

        @Test
        @DisplayName("Should find session by token id even if revoked")
        void shouldFindSessionByTokenId() {
            UUID tokenId = UUID.randomUUID();
            UserSession session = repository.save(session(
                    UUID.randomUUID(),
                    tokenId,
                    false,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            ));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByTokenId(tokenId);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(session.getId());
        }
    }

    @Nested
    @DisplayName("findByTokenIdAndRevokedFalse")
    class FindByTokenIdAndRevokedFalseTests {

        @Test
        @DisplayName("Should return unrevoked session by token id")
        void shouldReturnUnrevokedSession() {
            UUID tokenId = UUID.randomUUID();
            repository.save(session(
                    UUID.randomUUID(),
                    tokenId,
                    false,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            ));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByTokenIdAndRevokedFalse(tokenId);

            assertThat(result).isPresent();
            assertThat(result.get().isRevoked()).isFalse();
        }

        @Test
        @DisplayName("Should return empty for revoked session")
        void shouldReturnEmptyForRevokedSession() {
            UUID tokenId = UUID.randomUUID();
            repository.save(session(
                    UUID.randomUUID(),
                    tokenId,
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            ));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByTokenIdAndRevokedFalse(tokenId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByTokenIdAndRevokedFalseForUpdate")
    class FindByTokenIdAndRevokedFalseForUpdateTests {

        @Test
        @DisplayName("Should return unrevoked session for update lookup")
        void shouldReturnUnrevokedSessionForUpdateLookup() {
            UUID tokenId = UUID.randomUUID();
            repository.save(session(
                    UUID.randomUUID(),
                    tokenId,
                    false,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            ));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByTokenIdAndRevokedFalseForUpdate(tokenId);

            assertThat(result).isPresent();
            assertThat(result.get().isRevoked()).isFalse();
        }

        @Test
        @DisplayName("Should return empty for revoked session in update lookup")
        void shouldReturnEmptyForRevokedSessionInUpdateLookup() {
            UUID tokenId = UUID.randomUUID();
            repository.save(session(
                    UUID.randomUUID(),
                    tokenId,
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            ));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByTokenIdAndRevokedFalseForUpdate(tokenId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByUserIdAndRevokedFalseAndExpiresAtAfter")
    class CountActiveSessionsTests {

        @Test
        @DisplayName("Should count only unrevoked sessions that expire after the cutoff")
        void shouldCountOnlyUnrevokedFutureSessions() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(1), now.minusDays(3)));
            repository.save(session(userId, UUID.randomUUID(), false, null, now.minusDays(1), now.minusDays(2)));
            repository.save(session(userId, UUID.randomUUID(), true, now.minusHours(1), now.plusDays(1), now.minusDays(1)));
            repository.save(session(UUID.randomUUID(), UUID.randomUUID(), false, null, now.plusDays(1), now.minusDays(1)));
            repository.flush();

            long count = repository.countByUserIdAndRevokedFalseAndExpiresAtAfter(userId, now);

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("deleteExpiredOrRevokedSessions")
    class DeleteExpiredOrRevokedSessionsTests {

        @Test
        @DisplayName("Should delete expired sessions and properly revoked sessions only")
        void shouldDeleteExpiredAndRevokedSessionsOnly() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession expired = repository.save(session(
                    UUID.randomUUID(), UUID.randomUUID(), false, null, now.minusDays(1), now.minusDays(3)
            ));
            UserSession revoked = repository.save(session(
                    UUID.randomUUID(), UUID.randomUUID(), true, now.minusHours(2), now.plusDays(5), now.minusDays(2)
            ));
            UserSession active = repository.save(session(
                    UUID.randomUUID(), UUID.randomUUID(), false, null, now.plusDays(5), now.minusDays(1)
            ));
            repository.flush();

            repository.deleteExpiredOrRevokedSessions(now);
            entityManager.flush();
            entityManager.clear();

            assertThat(repository.findById(expired.getId())).isEmpty();
            assertThat(repository.findById(revoked.getId())).isEmpty();
            assertThat(repository.findById(active.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("revokeOldestSession")
    class RevokeOldestSessionTests {

        @Test
        @DisplayName("Should revoke only the oldest active session for the user")
        void shouldRevokeOnlyOldestActiveSession() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession oldest = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(3)));
            UserSession newest = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(1)));
            repository.save(session(userId, UUID.randomUUID(), false, null, now.minusDays(1), now.minusDays(10)));
            repository.flush();

            int updated = repository.revokeOldestSession(userId, now, "SESSION_LIMIT");
            entityManager.flush();
            entityManager.clear();

            assertThat(updated).isEqualTo(1);
            assertThat(repository.findById(oldest.getId())).get().extracting(UserSession::isRevoked).isEqualTo(true);
            assertThat(repository.findById(oldest.getId())).get().extracting(UserSession::getRevokedReason).isEqualTo("SESSION_LIMIT");
            assertThat(repository.findById(newest.getId())).get().extracting(UserSession::isRevoked).isEqualTo(false);
        }

        @Test
        @DisplayName("Should return zero when no active session is eligible")
        void shouldReturnZeroWhenNoActiveSessionIsEligible() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            repository.save(session(userId, UUID.randomUUID(), true, now.minusHours(1), now.plusDays(1), now.minusDays(2)));
            repository.save(session(userId, UUID.randomUUID(), false, null, now.minusDays(1), now.minusDays(3)));
            repository.flush();

            int updated = repository.revokeOldestSession(userId, now, "SESSION_LIMIT");

            assertThat(updated).isZero();
        }
    }

    @Nested
    @DisplayName("revokeAllActiveSessionsByUserId")
    class RevokeAllActiveSessionsTests {

        @Test
        @DisplayName("Should revoke all active future sessions for the requested user only")
        void shouldRevokeAllActiveFutureSessionsForRequestedUserOnly() {
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession first = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(3)));
            UserSession second = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(3), now.minusDays(2)));
            UserSession expired = repository.save(session(userId, UUID.randomUUID(), false, null, now.minusDays(1), now.minusDays(4)));
            UserSession otherUser = repository.save(session(otherUserId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(1)));
            repository.flush();

            repository.revokeAllActiveSessionsByUserId(userId, now, "LOGOUT_ALL");
            entityManager.flush();
            entityManager.clear();

            assertThat(repository.findById(first.getId())).get().extracting(UserSession::isRevoked).isEqualTo(true);
            assertThat(repository.findById(first.getId())).get().extracting(UserSession::getRevokedReason).isEqualTo("LOGOUT_ALL");
            assertThat(repository.findById(second.getId())).get().extracting(UserSession::isRevoked).isEqualTo(true);
            assertThat(repository.findById(expired.getId())).get().extracting(UserSession::isRevoked).isEqualTo(false);
            assertThat(repository.findById(otherUser.getId())).get().extracting(UserSession::isRevoked).isEqualTo(false);
        }
    }

    private UserSession session(
            UUID userId,
            UUID tokenId,
            boolean revoked,
            OffsetDateTime revokedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt
    ) {
        return UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType("PASSWORD")
                .deviceName("Device")
                .refreshTokenHash("hash-" + tokenId)
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .lastUsedAt(createdAt)
                .expiresAt(expiresAt)
                .revoked(revoked)
                .revokedAt(revokedAt)
                .revokedReason(revoked ? "REVOKED" : null)
                .createdAt(createdAt)
                .build();
    }
}
