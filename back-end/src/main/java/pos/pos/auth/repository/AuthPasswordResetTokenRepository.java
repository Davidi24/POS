package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthPasswordResetToken;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthPasswordResetTokenRepository extends JpaRepository<AuthPasswordResetToken, UUID> {

    @Modifying
    @Query("""
        DELETE FROM AuthPasswordResetToken t
        WHERE t.expiresAt < :now
    """)
    void deleteExpiredTokens(OffsetDateTime now);
}
