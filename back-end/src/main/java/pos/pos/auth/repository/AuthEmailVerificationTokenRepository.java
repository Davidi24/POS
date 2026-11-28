package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthEmailVerificationToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthEmailVerificationTokenRepository extends JpaRepository<AuthEmailVerificationToken, UUID> {

    // finds the token that came from request and checks if it is used or expired
    Optional<AuthEmailVerificationToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            OffsetDateTime now
    );

    boolean existsByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime cutoff);

    /** The person's waiting email change (a new address whose link hasn't been used and still works). */
    Optional<AuthEmailVerificationToken> findTopByUserIdAndNewEmailIsNotNullAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, OffsetDateTime now);

    /** A link by its key, used or not, so its page can say what it is. */
    Optional<AuthEmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    void deleteByUserId(UUID userId);

    @Modifying
    @Query("""
        DELETE FROM AuthEmailVerificationToken t
        WHERE t.expiresAt < :now
    """)
    void deleteExpiredTokens(OffsetDateTime now);
}
