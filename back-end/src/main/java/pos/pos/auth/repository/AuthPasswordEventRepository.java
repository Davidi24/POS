package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pos.pos.auth.entity.AuthPasswordEvent;
import pos.pos.auth.enums.PasswordEventKind;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthPasswordEventRepository extends JpaRepository<AuthPasswordEvent, UUID> {

    long countByUserIdAndKindAndHappenedAtAfter(UUID userId, PasswordEventKind kind, OffsetDateTime since);

    boolean existsByUserIdAndKindAndHappenedAtAfter(UUID userId, PasswordEventKind kind, OffsetDateTime since);

    @Modifying
    @Query("DELETE FROM AuthPasswordEvent e WHERE e.happenedAt < :cutoff")
    void deleteOlderThan(OffsetDateTime cutoff);
}
