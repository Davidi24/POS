package pos.pos.reservation.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.reservation.entity.ReservationPayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationPaymentRepository extends JpaRepository<ReservationPayment, UUID> {

    List<ReservationPayment> findAllByReservation_IdOrderByCreatedAtAsc(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from ReservationPayment payment where payment.reservation.id = :reservationId order by payment.createdAt asc")
    List<ReservationPayment> findAllByReservationIdForUpdate(@Param("reservationId") UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReservationPayment> findByIdAndReservation_Id(UUID id, UUID reservationId);
}
