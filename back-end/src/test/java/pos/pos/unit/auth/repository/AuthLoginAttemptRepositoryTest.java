package pos.pos.unit.auth.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.enums.LoginFailureReason;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.support.AbstractTestProfilePostgresTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthLoginAttemptRepositoryTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private AuthLoginAttemptRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Nested
    @DisplayName("countByIpAddressAndAttemptedAtAfter")
    class CountByIpAddressTests {

        @Test
        @DisplayName("Should count only matching IPs after the cutoff")
        void shouldCountOnlyMatchingIpsAfterCutoff() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusMinutes(10);

            repository.save(attempt("10.0.0.1", "cashier@pos.local", false, now.minusMinutes(5)));
            repository.save(attempt("10.0.0.1", "cashier@pos.local", false, now.minusMinutes(20)));
            repository.save(attempt("10.0.0.2", "cashier@pos.local", false, now.minusMinutes(5)));
            repository.flush();

            long count = repository.countByIpAddressAndAttemptedAtAfter("10.0.0.1", cutoff);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 0 when all attempts for the IP are outside the window")
        void shouldReturnZero_whenAllAttemptsOutsideWindow() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusMinutes(10);

            repository.save(attempt("10.0.0.1", "cashier@pos.local", false, now.minusMinutes(20)));
            repository.save(attempt("10.0.0.1", "cashier@pos.local", false, now.minusMinutes(30)));
            repository.flush();

            long count = repository.countByIpAddressAndAttemptedAtAfter("10.0.0.1", cutoff);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Should return 0 when no attempts exist for the IP")
        void shouldReturnZero_whenNoAttemptsForIp() {
            OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);

            long count = repository.countByIpAddressAndAttemptedAtAfter("99.99.99.99", cutoff);

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("countByIdentifierAndAttemptedAtAfterAndSuccessFalse")
    class CountByIdentifierTests {

        @Test
        @DisplayName("Should count only failed attempts for matching identifier after the cutoff")
        void shouldCountOnlyFailedAttemptsForMatchingIdentifierAfterCutoff() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusMinutes(10);

            repository.save(attempt("10.0.0.1", "cashier@pos.local", false, now.minusMinutes(5)));
            repository.save(attempt("10.0.0.1", "cashier@pos.local", true, now.minusMinutes(5)));
            repository.save(attempt("10.0.0.1", "cashier@pos.local", false, now.minusMinutes(20)));
            repository.save(attempt("10.0.0.1", "admin@pos.local", false, now.minusMinutes(5)));
            repository.flush();

            long count = repository.countByIdentifierAndAttemptedAtAfterAndSuccessFalse("cashier@pos.local", cutoff);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 0 when all recent attempts for the identifier are successful")
        void shouldReturnZeroWhenAllRecentAttemptsAreSuccessful() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusMinutes(10);

            repository.save(attempt("10.0.0.1", "cashier@pos.local", true, now.minusMinutes(5)));
            repository.save(attempt("10.0.0.1", "cashier@pos.local", true, now.minusMinutes(3)));
            repository.flush();

            long count = repository.countByIdentifierAndAttemptedAtAfterAndSuccessFalse("cashier@pos.local", cutoff);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Should return 0 when identifier does not match")
        void shouldReturnZeroWhenIdentifierDoesNotMatch() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusMinutes(10);

            repository.save(attempt("10.0.0.1", "other@pos.local", false, now.minusMinutes(5)));
            repository.flush();

            long count = repository.countByIdentifierAndAttemptedAtAfterAndSuccessFalse("cashier@pos.local", cutoff);

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("countByUserIdAndAttemptedAtAfterAndSuccessFalse")
    class CountByUserIdTests {

        @Test
        @DisplayName("Should count failed attempts for the same user across multiple identifiers")
        void shouldCountFailedAttemptsForSameUserAcrossIdentifiers() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusMinutes(10);

            repository.save(attempt(userId, "10.0.0.1", "cashier.one", false, now.minusMinutes(5)));
            repository.save(attempt(userId, "10.0.0.1", "cashier@pos.local", false, now.minusMinutes(3)));
            repository.save(attempt(userId, "10.0.0.1", "cashier.one", true, now.minusMinutes(1)));
            repository.save(attempt(UUID.randomUUID(), "10.0.0.1", "other.user", false, now.minusMinutes(2)));
            repository.flush();

            long count = repository.countByUserIdAndAttemptedAtAfterAndSuccessFalse(userId, cutoff);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("deleteOlderThan")
    class DeleteOlderThanTests {

        @Test
        @DisplayName("Should delete only attempts older than the cutoff")
        void shouldDeleteOnlyAttemptsOlderThanCutoff() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusDays(90);

            AuthLoginAttempt oldAttempt = repository.save(attempt("10.0.0.1", "old@pos.local", false, now.minusDays(91)));
            AuthLoginAttempt recentAttempt = repository.save(attempt("10.0.0.2", "recent@pos.local", false, now.minusDays(30)));
            repository.flush();

            repository.deleteOlderThan(cutoff);
            entityManager.flush();
            entityManager.clear();

            assertThat(repository.findById(oldAttempt.getId())).isEmpty();
            assertThat(repository.findById(recentAttempt.getId())).isPresent();
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should delete nothing when all attempts are within the cutoff")
        void shouldDeleteNothing_whenAllAttemptsAreRecent() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusDays(90);

            repository.save(attempt("10.0.0.1", "a@pos.local", false, now.minusDays(10)));
            repository.save(attempt("10.0.0.2", "b@pos.local", false, now.minusDays(30)));
            repository.flush();

            repository.deleteOlderThan(cutoff);
            entityManager.flush();
            entityManager.clear();

            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should delete all attempts when all are older than the cutoff")
        void shouldDeleteAll_whenAllAttemptsAreOld() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusDays(90);

            repository.save(attempt("10.0.0.1", "a@pos.local", false, now.minusDays(91)));
            repository.save(attempt("10.0.0.2", "b@pos.local", false, now.minusDays(120)));
            repository.flush();

            repository.deleteOlderThan(cutoff);
            entityManager.flush();
            entityManager.clear();

            assertThat(repository.count()).isZero();
        }
    }

    private AuthLoginAttempt attempt(
            String ipAddress,
            String identifier,
            boolean success,
            OffsetDateTime attemptedAt
    ) {
        return attempt(UUID.randomUUID(), ipAddress, identifier, success, attemptedAt);
    }

    private AuthLoginAttempt attempt(
            UUID userId,
            String ipAddress,
            String identifier,
            boolean success,
            OffsetDateTime attemptedAt
    ) {
        return AuthLoginAttempt.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .identifier(identifier)
                .ipAddress(ipAddress)
                .userAgent("JUnit")
                .success(success)
                .failureReason(success ? null : LoginFailureReason.INVALID_CREDENTIALS)
                .attemptedAt(attemptedAt)
                .build();
    }
}
