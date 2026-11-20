package pos.pos.reservation.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// A manager cleared a guest's no-show warning, with a reason. The no-show bookings stay in the history; only
// no-shows after the latest clear count. The guest is matched by customer, or by phone/email.
@Entity
@Table(name = "guest_no_show_clears")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GuestNoShowClear {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "customer_id", columnDefinition = "uuid")
    private UUID customerId;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "cleared_by", columnDefinition = "uuid")
    private UUID clearedBy;

    @Column(name = "cleared_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime clearedAt;

    @PrePersist
    protected void prePersist() {
        reason = NormalizationUtils.normalize(reason);
        if (reason == null) {
            throw new IllegalStateException("reason is required");
        }
        if (customerId == null && contactPhone == null && contactEmail == null) {
            throw new IllegalStateException("a customer, phone or email is required");
        }
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
        if (clearedAt == null) {
            clearedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
