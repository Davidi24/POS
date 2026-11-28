package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthLoginAttempt;

import java.time.OffsetDateTime;
import java.util.UUID;

// checked
// tested
public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, UUID> {

    /** A person's own sign-in tries since [after], newest first, a page at a time (Profile → Security → Recent sign-ins). */
    org.springframework.data.domain.Page<AuthLoginAttempt> findByUserIdAndAttemptedAtAfterOrderByAttemptedAtDesc(UUID userId, OffsetDateTime after,
            org.springframework.data.domain.Pageable pageable);

    /** How many of a person's tries since [after] worked, and how many failed for each reason. */
    @Query("SELECT a.success, a.failureReason, COUNT(a) FROM AuthLoginAttempt a WHERE a.userId = :userId AND a.attemptedAt > :after"
            + " GROUP BY a.success, a.failureReason")
    java.util.List<Object[]> countResultsForUser(UUID userId, OffsetDateTime after);
    long countByIpAddressAndAttemptedAtAfter(String ipAddress, OffsetDateTime after);

    long countByUserIdAndAttemptedAtAfterAndSuccessFalse(UUID userId, OffsetDateTime after);

    long countByIdentifierAndAttemptedAtAfterAndSuccessFalse(String identifier, OffsetDateTime after);

    /** When a known person typed a wrong password, after [after], oldest first (for the lock rule). */
    @Query("SELECT a.attemptedAt FROM AuthLoginAttempt a WHERE a.userId = :userId AND a.success = false"
            + " AND a.failureReason = pos.pos.auth.enums.LoginFailureReason.INVALID_CREDENTIALS AND a.attemptedAt > :after ORDER BY a.attemptedAt")
    java.util.List<OffsetDateTime> wrongPasswordTimesForUser(UUID userId, OffsetDateTime after);

    /** The same for a username or email nobody has, so it locks exactly like a real one and tells nothing. */
    @Query("SELECT a.attemptedAt FROM AuthLoginAttempt a WHERE a.userId IS NULL AND a.identifier = :identifier AND a.success = false"
            + " AND a.failureReason = pos.pos.auth.enums.LoginFailureReason.INVALID_CREDENTIALS AND a.attemptedAt > :after ORDER BY a.attemptedAt")
    java.util.List<OffsetDateTime> wrongPasswordTimesForUnknown(String identifier, OffsetDateTime after);

    /** Wrong PINs typed on one connected till after [after], oldest first (5 within 15 minutes pause it). */
    @Query("SELECT a.attemptedAt FROM AuthLoginAttempt a WHERE a.deviceId = :deviceId AND a.success = false"
            + " AND a.failureReason = pos.pos.auth.enums.LoginFailureReason.INVALID_PIN AND a.attemptedAt > :after ORDER BY a.attemptedAt")
    java.util.List<OffsetDateTime> wrongPinTimesForDevice(String deviceId, OffsetDateTime after);

    /** Wrong passwords typed on one device after [after], oldest first (the per-device limit). */
    @Query("SELECT a.attemptedAt FROM AuthLoginAttempt a WHERE a.deviceId = :deviceId AND a.success = false"
            + " AND a.failureReason = pos.pos.auth.enums.LoginFailureReason.INVALID_CREDENTIALS AND a.attemptedAt > :after ORDER BY a.attemptedAt")
    java.util.List<OffsetDateTime> wrongPasswordTimesForDevice(String deviceId, OffsetDateTime after);

    /** Wrong passwords from one network address after [after], oldest first (the per-address safety net). */
    @Query("SELECT a.attemptedAt FROM AuthLoginAttempt a WHERE a.ipAddress = :ipAddress AND a.success = false"
            + " AND a.failureReason = pos.pos.auth.enums.LoginFailureReason.INVALID_CREDENTIALS AND a.attemptedAt > :after ORDER BY a.attemptedAt")
    java.util.List<OffsetDateTime> wrongPasswordTimesForAddress(String ipAddress, OffsetDateTime after);

    @Modifying
    @Query("DELETE FROM AuthLoginAttempt a WHERE a.attemptedAt < :cutoff")
    void deleteOlderThan(OffsetDateTime cutoff);
}
