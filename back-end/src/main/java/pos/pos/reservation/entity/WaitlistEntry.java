package pos.pos.reservation.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.WaitlistStatus;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// A walk-in group waiting at the door for a table, e.g. "Maria · 4 guests · since 18:40".
@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WaitlistEntry {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "branch_id", nullable = false, columnDefinition = "uuid")
    private UUID branchId;

    @Column(name = "guest_name", nullable = false, length = 150)
    private String guestName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "table_id", columnDefinition = "uuid")
    private UUID tableId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "seated_at", columnDefinition = "timestamptz")
    private OffsetDateTime seatedAt;

    @Column(name = "left_at", columnDefinition = "timestamptz")
    private OffsetDateTime leftAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @PrePersist
    protected void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        validate();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        validate();
    }

    private void validate() {
        guestName = NormalizationUtils.normalize(guestName);
        contactPhone = NormalizationUtils.normalizePhone(contactPhone);
        note = NormalizationUtils.normalize(note);
        if (guestName == null) {
            throw new IllegalStateException("guestName is required");
        }
        if (partySize <= 0) {
            throw new IllegalStateException("partySize must be greater than zero");
        }
    }
}
