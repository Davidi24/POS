package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.ReservationEventType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationEventRepository extends JpaRepository<ReservationEvent, UUID> {
    boolean existsByReservation_IdAndTypeAndCreatedAtGreaterThanEqual(UUID reservationId, ReservationEventType type, java.time.OffsetDateTime since);

    List<ReservationEvent> findAllByReservation_IdOrderByCreatedAtAsc(UUID reservationId);

    boolean existsByReservation_IdAndType(UUID reservationId, ReservationEventType type);

    boolean existsByReservation_IdAndTypeAndCreatedAtBefore(UUID reservationId, ReservationEventType type, java.time.OffsetDateTime before);

    List<ReservationEvent> findAllByReservation_IdInAndTypeIn(Collection<UUID> reservationIds, Collection<ReservationEventType> types);
}
