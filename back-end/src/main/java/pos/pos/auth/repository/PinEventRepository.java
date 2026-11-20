package pos.pos.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.auth.entity.PinEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PinEventRepository extends JpaRepository<PinEvent, UUID> {

    long countByUserIdAndKindAndHappenedAtAfter(UUID userId, PinEvent.Kind kind, OffsetDateTime after);

    /** This PIN was suggested to this person since [after] (only a suggested PIN can be saved). */
    boolean existsByUserIdAndKindAndPinHashAndHappenedAtAfter(UUID userId, PinEvent.Kind kind, String pinHash, OffsetDateTime after);

    java.util.Optional<PinEvent> findTopByUserIdAndKindInOrderByHappenedAtDesc(UUID userId, java.util.Collection<PinEvent.Kind> kinds);
}
