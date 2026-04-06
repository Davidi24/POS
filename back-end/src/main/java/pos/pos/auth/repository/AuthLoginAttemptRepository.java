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
    long countByIpAddressAndAttemptedAtAfter(String ipAddress, OffsetDateTime after);

    long countByEmailAndAttemptedAtAfterAndSuccessFalse(String email, OffsetDateTime after);

    @Modifying
    @Query("DELETE FROM AuthLoginAttempt a WHERE a.attemptedAt < :cutoff")
    void deleteOlderThan(OffsetDateTime cutoff);
}
