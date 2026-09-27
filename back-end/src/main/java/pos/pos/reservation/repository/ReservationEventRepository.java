package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.ReservationEventType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationEventRepository extends JpaRepository<ReservationEvent, UUID> {

    List<ReservationEvent> findAllByReservation_IdOrderByCreatedAtAsc(UUID reservationId);

    boolean existsByReservation_IdAndType(UUID reservationId, ReservationEventType type);

    List<ReservationEvent> findAllByReservation_IdInAndTypeIn(Collection<UUID> reservationIds, Collection<ReservationEventType> types);
}
