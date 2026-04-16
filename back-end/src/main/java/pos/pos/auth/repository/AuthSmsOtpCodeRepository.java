package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.SmsOtpPurpose;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthSmsOtpCodeRepository extends JpaRepository<AuthSmsOtpCode, UUID> {

    // "Give me the most recent SMS code sent to user 123 for password reset, as long as it hasn't been used yet and hasn't expired."
    Optional<AuthSmsOtpCode> findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId,
            SmsOtpPurpose purpose,
            OffsetDateTime now
    );

    boolean existsByUserIdAndPurposeAndCreatedAtAfter(UUID userId, SmsOtpPurpose purpose, OffsetDateTime cutoff);

    int countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, SmsOtpPurpose purpose, OffsetDateTime cutoff);

    @Modifying
    void deleteByUserIdAndPurpose(UUID userId, SmsOtpPurpose purpose);

    @Modifying
    @Query("""
        DELETE FROM AuthSmsOtpCode c
        WHERE c.expiresAt < :now
    """)
    void deleteExpiredCodes(OffsetDateTime now);
}
