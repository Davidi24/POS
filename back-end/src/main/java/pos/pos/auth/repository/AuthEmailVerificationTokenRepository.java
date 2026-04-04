package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthEmailVerificationToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthEmailVerificationTokenRepository extends JpaRepository<AuthEmailVerificationToken, UUID> {

    Optional<AuthEmailVerificationToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            OffsetDateTime now
    );

    boolean existsByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime cutoff);

    @Modifying
    void deleteByUserId(UUID userId);

    @Modifying
    @Query("""
        DELETE FROM AuthEmailVerificationToken t
        WHERE t.expiresAt < :now
    """)
    void deleteExpiredTokens(OffsetDateTime now);
}
