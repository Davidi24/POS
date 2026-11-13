package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationReminder;

public interface ReservationReminderRepository extends JpaRepository<ReservationReminder, ReservationReminder.Key> {
}
