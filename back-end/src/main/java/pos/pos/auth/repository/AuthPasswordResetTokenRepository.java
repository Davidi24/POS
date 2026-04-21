package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthPasswordResetToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthPasswordResetTokenRepository extends JpaRepository<AuthPasswordResetToken, UUID> {

    Optional<AuthPasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            OffsetDateTime now
    );

    boolean existsByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime cutoff);

    @Modifying
    void deleteByUserId(UUID userId);

    @Modifying
    @Query("""
        DELETE FROM AuthPasswordResetToken t
        WHERE t.expiresAt < :now
    """)
    void deleteExpiredTokens(OffsetDateTime now);
}
