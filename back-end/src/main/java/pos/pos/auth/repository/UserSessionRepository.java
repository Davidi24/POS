package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.auth.entity.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenIdAndRevokedFalse(UUID tokenId);

    List<UserSession> findByUserId(UUID userId);
}
