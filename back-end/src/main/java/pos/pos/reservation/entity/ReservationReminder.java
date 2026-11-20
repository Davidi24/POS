package pos.pos.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// A once-a-day reminder already sent for a restaurant (e.g. the day-before summary at 15:00).
@Entity
@Table(name = "reservation_reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationReminder {

    @EmbeddedId
    private Key key;

    @Column(name = "sent_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
        private UUID restaurantId;

        @Column(name = "kind", nullable = false, length = 40)
        private String kind;

        @Column(name = "service_date", nullable = false)
        private LocalDate serviceDate;
    }
}
