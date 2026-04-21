package pos.pos.unit.auth.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.support.AbstractTestProfilePostgresTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSessionRepositoryTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private UserSessionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("Should block a concurrent update lookup until the first transaction releases the lock")
        void shouldBlockConcurrentUpdateLookupUntilLockReleased() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UserSession persistedSession = inNewTransaction(() -> repository.saveAndFlush(session(
                    userId,
                    tokenId,
                    false,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            )));

            CountDownLatch firstTransactionHoldingLock = new CountDownLatch(1);
            CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
            CountDownLatch secondTransactionStarted = new CountDownLatch(1);

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            try {
                Future<Optional<UserSession>> firstLookup = executorService.submit(() ->
                        inNewTransaction(() -> {
                            Optional<UserSession> lockedSession = repository.findByTokenIdAndRevokedFalseForUpdate(tokenId);
                            firstTransactionHoldingLock.countDown();

                            try {
                                if (!releaseFirstTransaction.await(5, TimeUnit.SECONDS)) {
                                    throw new AssertionError("Timed out waiting to release the first transaction");
                                }
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError("Interrupted while waiting to release the first transaction", ex);
                            }

                            return lockedSession;
                        })
                );

                assertThat(firstTransactionHoldingLock.await(5, TimeUnit.SECONDS)).isTrue();

                Future<Optional<UserSession>> secondLookup = executorService.submit(() ->
                        inNewTransaction(() -> {
                            secondTransactionStarted.countDown();
                            return repository.findByTokenIdAndRevokedFalseForUpdate(tokenId);
                        })
                );

                assertThat(secondTransactionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(200);

                assertThat(secondLookup.isDone()).isFalse();

                releaseFirstTransaction.countDown();

                assertThat(firstLookup.get(5, TimeUnit.SECONDS))
                        .isPresent()
                        .get()
                        .extracting(UserSession::getId)
                        .isEqualTo(persistedSession.getId());

                assertThat(secondLookup.get(5, TimeUnit.SECONDS))
                        .isPresent()
                        .get()
                        .extracting(UserSession::getId)
                        .isEqualTo(persistedSession.getId());
            } finally {
                executorService.shutdownNow();
                assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("database constraints")
    class DatabaseConstraintTests {

        @Test
        @DisplayName("Should reject duplicate token ids")
        void shouldRejectDuplicateTokenIds() {
            UUID tokenId = UUID.randomUUID();

            repository.save(session(
                    UUID.randomUUID(),
                    tokenId,
                    false,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
            ));

            assertThatThrownBy(() -> repository.saveAndFlush(session(
                    UUID.randomUUID(),
                    tokenId,
                    false,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            )))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should reject revoked sessions without revokedAt")
        void shouldRejectRevokedSessionWithoutRevokedAt() {
            assertThatThrownBy(() -> repository.saveAndFlush(session(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    true,
                    null,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(7),
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            )))
                    .isInstanceOf(DataIntegrityViolationException.class);
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

    @Nested
    @DisplayName("findActiveSessionsByUserId")
    class FindActiveSessionsByUserIdTests {

        @Test
        @DisplayName("Should return only active non-expired sessions ordered by lastUsedAt DESC")
        void shouldReturnActiveSessionsOrderedByLastUsedAt() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession oldest = repository.save(sessionWithLastUsedAt(userId, UUID.randomUUID(), false, now.plusDays(7), now.minusDays(3)));
            UserSession newest = repository.save(sessionWithLastUsedAt(userId, UUID.randomUUID(), false, now.plusDays(7), now.minusDays(1)));
            repository.save(sessionWithLastUsedAt(userId, UUID.randomUUID(), true, now.plusDays(7), now.minusDays(2)));
            repository.save(sessionWithLastUsedAt(userId, UUID.randomUUID(), false, now.minusDays(1), now.minusDays(2)));
            repository.save(sessionWithLastUsedAt(UUID.randomUUID(), UUID.randomUUID(), false, now.plusDays(7), now.minusDays(1)));
            repository.flush();
            entityManager.clear();

            List<UserSession> result = repository.findActiveSessionsByUserId(userId, now);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(newest.getId());
            assertThat(result.get(1).getId()).isEqualTo(oldest.getId());
        }

        @Test
        @DisplayName("Should return empty list when user has no active sessions")
        void shouldReturnEmptyWhenNoActiveSessions() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            repository.flush();

            List<UserSession> result = repository.findActiveSessionsByUserId(userId, now);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndUserIdAndRevokedFalse")
    class FindByIdAndUserIdAndRevokedFalseTests {

        @Test
        @DisplayName("Should return session when id, userId match and not revoked")
        void shouldReturnSessionWhenOwned() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession session = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(1)));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByIdAndUserIdAndRevokedFalse(session.getId(), userId);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("Should return empty when session belongs to different user")
        void shouldReturnEmptyForWrongUser() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession session = repository.save(session(UUID.randomUUID(), UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(1)));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByIdAndUserIdAndRevokedFalse(session.getId(), userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when session is revoked")
        void shouldReturnEmptyWhenRevoked() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession session = repository.save(session(userId, UUID.randomUUID(), true, now.minusHours(1), now.plusDays(7), now.minusDays(1)));
            repository.flush();
            entityManager.clear();

            Optional<UserSession> result = repository.findByIdAndUserIdAndRevokedFalse(session.getId(), userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("revokeAllActiveSessionsByUserIdExcept")
    class RevokeAllActiveSessionsByUserIdExceptTests {

        @Test
        @DisplayName("Should revoke all active sessions except the excluded one")
        void shouldRevokeAllExceptExcluded() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            UserSession current = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(1)));
            UserSession other1 = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(2)));
            UserSession other2 = repository.save(session(userId, UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(3)));
            UserSession expired = repository.save(session(userId, UUID.randomUUID(), false, null, now.minusDays(1), now.minusDays(4)));
            UserSession otherUser = repository.save(session(UUID.randomUUID(), UUID.randomUUID(), false, null, now.plusDays(7), now.minusDays(1)));
            repository.flush();

            repository.revokeAllActiveSessionsByUserIdExcept(userId, current.getId(), now, "SESSION_REVOKED");
            entityManager.flush();
            entityManager.clear();

            assertThat(repository.findById(current.getId())).get().extracting(UserSession::isRevoked).isEqualTo(false);
            assertThat(repository.findById(other1.getId())).get().extracting(UserSession::isRevoked).isEqualTo(true);
            assertThat(repository.findById(other1.getId())).get().extracting(UserSession::getRevokedReason).isEqualTo("SESSION_REVOKED");
            assertThat(repository.findById(other2.getId())).get().extracting(UserSession::isRevoked).isEqualTo(true);
            assertThat(repository.findById(expired.getId())).get().extracting(UserSession::isRevoked).isEqualTo(false);
            assertThat(repository.findById(otherUser.getId())).get().extracting(UserSession::isRevoked).isEqualTo(false);
        }
    }

    private UserSession sessionWithLastUsedAt(UUID userId, UUID tokenId, boolean revoked, OffsetDateTime expiresAt, OffsetDateTime lastUsedAt) {
        return UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(SessionType.PASSWORD)
                .deviceName("Device")
                .refreshTokenHash("hash-" + tokenId)
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .lastUsedAt(lastUsedAt)
                .expiresAt(expiresAt)
                .revoked(revoked)
                .revokedAt(revoked ? lastUsedAt : null)
                .revokedReason(revoked ? "REVOKED" : null)
                .createdAt(lastUsedAt)
                .build();
    }

    private <T> T inNewTransaction(TransactionCallback<T> callback) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> callback.execute());
    }

    private void inNewTransaction(VoidTransactionCallback callback) {
        inNewTransaction(() -> {
            callback.execute();
            return null;
        });
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
                .sessionType(SessionType.PASSWORD)
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

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T execute();
    }

    @FunctionalInterface
    private interface VoidTransactionCallback {
        void execute();
    }
}
