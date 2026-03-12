package pos.pos.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.user.entity.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHashAndRevokedFalse(String refreshTokenHash);

    List<UserSession> findByUserId(UUID userId);

}