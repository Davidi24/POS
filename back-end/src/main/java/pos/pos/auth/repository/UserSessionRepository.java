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

// checked
// tested
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenId(UUID tokenId);

    Optional<UserSession> findByTokenIdAndRevokedFalse(UUID tokenId);

    long countByUserIdAndRevokedFalseAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    Optional<UserSession> findByIdAndUserIdAndRevokedFalse(UUID id, UUID userId);

    //Fetches a non-revoked session by tokenId and locks it so no one else can modify it concurrently, so it locks in the db so no one uses that row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM UserSession s
        WHERE s.tokenId = :tokenId
          AND s.revoked = false
    """)
    Optional<UserSession> findByTokenIdAndRevokedFalseForUpdate(UUID tokenId);


    @Modifying
    @Query("""
        DELETE FROM UserSession s
        WHERE s.expiresAt < :now
           OR (s.revoked = true AND s.revokedAt IS NOT NULL)
    """)
    void deleteExpiredOrRevokedSessions(OffsetDateTime now);

    // Enforces a maximum number of active sessions (e.g., 5 per user).
    // When a new session is created and the limit is exceeded,
    // the oldest active (non-revoked, non-expired) session is revoked
    // to make room for the new one.
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

    // It removes all session for a specific user so the user is logged in no device
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
    void revokeAllActiveSessionsByUserId(UUID userId, OffsetDateTime now, String reason);

    @Query("""
        SELECT s FROM UserSession s
        WHERE s.userId = :userId
          AND s.revoked = false
          AND s.expiresAt > :now
        ORDER BY s.lastUsedAt DESC
    """)
    List<UserSession> findActiveSessionsByUserId(UUID userId, OffsetDateTime now);

    // it does remove all session expect a specific one that is taken as argument
    @Modifying
    @Query("""
        UPDATE UserSession s
        SET s.revoked = true,
            s.revokedAt = :now,
            s.revokedReason = :reason
        WHERE s.userId = :userId
          AND s.id != :excludeSessionId
          AND s.revoked = false
          AND s.expiresAt > :now
    """)
    void revokeAllActiveSessionsByUserIdExcept(UUID userId, UUID excludeSessionId, OffsetDateTime now, String reason);
}
