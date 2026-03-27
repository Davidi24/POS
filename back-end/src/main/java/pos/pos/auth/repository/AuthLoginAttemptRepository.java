package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.auth.entity.AuthLoginAttempt;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, UUID> {
    long countByIpAddressAndAttemptedAtAfter(String ipAddress, OffsetDateTime after);

    long countByEmailAndAttemptedAtAfterAndSuccessFalse(String email, OffsetDateTime after);
}
