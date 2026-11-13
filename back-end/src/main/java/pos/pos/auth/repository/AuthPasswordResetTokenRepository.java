package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthPasswordResetToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthPasswordResetTokenRepository extends JpaRepository<AuthPasswordResetToken, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthPasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            OffsetDateTime now
    );

    Optional<AuthPasswordResetToken> findByTokenHash(String tokenHash);

    // Locks the link, so two people pressing "Set password" at once can't both use it.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AuthPasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<AuthPasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    boolean existsByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime cutoff);

    boolean existsByUserIdAndPurpose(UUID userId, pos.pos.auth.enums.PasswordLinkPurpose purpose);

    Optional<AuthPasswordResetToken> findTopByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, pos.pos.auth.enums.PasswordLinkPurpose purpose);

    // The newest link is the only one that works; older ones are kept to explain why they don't.
    @Modifying
    @Query("""
        UPDATE AuthPasswordResetToken t SET t.supersededAt = :now
        WHERE t.userId = :userId AND t.usedAt IS NULL AND t.supersededAt IS NULL
    """)
    int supersedeOpenLinks(UUID userId, OffsetDateTime now);

    @Modifying
    void deleteByUserId(UUID userId);

    @Modifying
    @Query("""
        DELETE FROM AuthPasswordResetToken t
        WHERE t.expiresAt < :now
    """)
    void deleteExpiredTokens(OffsetDateTime now);

    // Used and replaced links, once nobody needs to be told about them any more.
    @Modifying
    @Query("""
        DELETE FROM AuthPasswordResetToken t
        WHERE t.usedAt < :cutoff OR t.supersededAt < :cutoff
    """)
    void deleteSpentTokens(OffsetDateTime cutoff);
}
