package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.auth.entity.AuthLoginAttempt;

import java.util.UUID;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, UUID> {
}
