package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.UserSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenIdAndRevokedFalse(UUID tokenId);

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
            revoked_at = :now
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
    int revokeOldestSession(UUID userId, OffsetDateTime now);

    List<UserSession> findByUserId(UUID userId);
}