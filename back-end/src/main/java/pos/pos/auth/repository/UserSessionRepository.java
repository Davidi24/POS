package pos.pos.auth.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.UserSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenId(UUID tokenId);

    Optional<UserSession> findByTokenIdAndRevokedFalse(UUID tokenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM UserSession s
        WHERE s.tokenId = :tokenId
          AND s.revoked = false
    """)
    Optional<UserSession> findByTokenIdAndRevokedFalseForUpdate(UUID tokenId);

    long countByUserIdAndRevokedFalseAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    @Modifying
    @Query("""
        DELETE FROM UserSession s
        WHERE s.expiresAt < :now
           OR (s.revoked = true AND s.revokedAt IS NOT NULL)
    """)
    void deleteExpiredOrRevokedSessions(OffsetDateTime now);

    @Modifying
    @Query(value = """
        UPDATE user_sessions
        SET revoked = true,
            revoked_at = :now,
            revoked_reason = :reason
        WHERE id = (
            SELECT id
            FROM user_sessions
            WHERE user_id = :userId
              AND revoked = false
              AND expires_at > :now
            ORDER BY created_at ASC
            LIMIT 1
        )
    """, nativeQuery = true)
    int revokeOldestSession(UUID userId, OffsetDateTime now, String reason);

    List<UserSession> findByUserId(UUID userId);

    @Modifying
    @Query("""
    UPDATE UserSession s
    SET s.revoked = true,
        s.revokedAt = :now,
        s.revokedReason = :reason
    WHERE s.userId = :userId
      AND s.revoked = false
      AND s.expiresAt > :now
""")
    int revokeAllActiveSessionsByUserId(UUID userId, OffsetDateTime now, String reason);
}
